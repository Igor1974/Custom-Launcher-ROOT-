package de.blinkt.openvpn.core;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.NetworkSpace;
import de.blinkt.openvpn.core.VpnStatus;
import java.util.Locale;
import java.util.Vector;
import ru.yourok.openvpn.BuildConfig;
import ru.yourok.openvpn.R;
import ru.yourok.openvpn.VPNActivityService;

public class OpenVPNService extends VpnService implements VpnStatus.StateListener, Handler.Callback, VpnStatus.ByteCountListener, IOpenVPNServiceInternal {
    public static final String ALWAYS_SHOW_NOTIFICATION = "de.blinkt.openvpn.NOTIFICATION_ALWAYS_VISIBLE";
    public static final String DISCONNECT_VPN = "de.blinkt.openvpn.DISCONNECT_VPN";
    public static final String NOTIFICATION_CHANNEL_BG_ID = "num_vpn_bg";
    public static final String NOTIFICATION_CHANNEL_NEWSTATUS_ID = "num_vpn_newstat";
    public static final String START_SERVICE = "de.blinkt.openvpn.START_SERVICE";
    
    private static Class mNotificationActivityClass = null;
    private static boolean mNotificationAlwaysVisible = false;
    
    private Handler guiHandler;
    private long mConnecttime;
    private DeviceStateReceiver mDeviceStateReceiver;
    private String mLastTunCfg;
    private OpenVPNManagement mManagement;
    private int mMtu;
    private VpnProfile mProfile;
    private String mRemoteGW;
    private Toast mlastToast;
    private final Vector<String> mDnslist = new Vector<>();
    private final NetworkSpace mRoutes = new NetworkSpace();
    private final NetworkSpace mRoutesv6 = new NetworkSpace();
    private final Object mProcessLock = new Object();
    private Thread mProcessThread = null;
    private String mDomain = null;
    private CIDRIP mLocalIP = null;
    private String mLocalIPv6 = null;
    private boolean mDisplayBytecount = false;
    private boolean mStarting = false;

    private final IBinder mBinder = new IOpenVPNServiceInternal.Stub() {
        @Override
        public boolean protect(int i) throws RemoteException {
            return OpenVPNService.this.protect(i);
        }

        @Override
        public void userPause(boolean z) throws RemoteException {
            OpenVPNService.this.userPause(z);
        }

        @Override
        public boolean stopVPN(boolean z) throws RemoteException {
            return OpenVPNService.this.stopVPNInternal(z);
        }
    };

    @Override
    public IBinder asBinder() {
        return mBinder;
    }

    @Override
    public void setConnectedVPN(String str) {
    }

    public static String humanReadableByteCount(long j, boolean z, Resources resources) {
        if (z) j *= 8;
        double d = j;
        double d2 = z ? 1000 : 1024;
        int iMax = Math.max(0, Math.min((int) (Math.log(d) / Math.log(d2)), 3));
        float fPow = (float) (d / Math.pow(d2, iMax));
        if (z) {
            if (iMax == 0) return resources.getString(R.string.bits_per_second, fPow);
            if (iMax == 1) return resources.getString(R.string.kbits_per_second, fPow);
            if (iMax == 2) return resources.getString(R.string.mbits_per_second, fPow);
            return resources.getString(R.string.gbits_per_second, fPow);
        } else {
            if (iMax == 0) return resources.getString(R.string.volume_byte, fPow);
            if (iMax == 1) return resources.getString(R.string.volume_kbyte, fPow);
            if (iMax == 2) return resources.getString(R.string.volume_mbyte, fPow);
            return resources.getString(R.string.volume_gbyte, fPow);
        }
    }

    public static void setNotificationActivityClass(Class<? extends Activity> cls) {
        mNotificationActivityClass = cls;
    }

    @Override // android.net.VpnService, android.app.Service
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        return (action == null || !action.equals(START_SERVICE)) ? super.onBind(intent) : mBinder;
    }

    @Override // android.net.VpnService
    public void onRevoke() {
        VpnStatus.logError(R.string.permission_revoked);
        stopVPNInternal(false);
        endVpnService();
    }

    public void openvpnStopped() {
        endVpnService();
    }

    private void endVpnService() {
        synchronized (this.mProcessLock) {
            this.mProcessThread = null;
        }
        VpnStatus.removeByteCountListener(this);
        unregisterDeviceStateReceiver();
        ProfileManager.setConnectedVpnProfileDisconnected(this);
        if (!this.mStarting) {
            stopForeground(!mNotificationAlwaysVisible);
            if (!mNotificationAlwaysVisible) {
                stopSelf();
                VpnStatus.removeStateListener(this);
            }
        }
    }

    private void showNotification(final String str, String str2, String str3, long j, ConnectionStatus connectionStatus) {
        String string;
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), "ru.yourok.num.activity.SettingsActivity");
        intent.setPackage(getPackageName());
        intent.putExtra("connect_antizapret", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent activity = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannelM = new NotificationChannel(BuildConfig.LIBRARY_PACKAGE_NAME, "VPN Background Service", NotificationManager.IMPORTANCE_LOW);
            notificationChannelM.setLightColor(-16776961);
            notificationChannelM.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(notificationChannelM);
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, BuildConfig.LIBRARY_PACKAGE_NAME);
            int priority = str3.equals(NOTIFICATION_CHANNEL_BG_ID) ? NotificationCompat.PRIORITY_MIN : NotificationCompat.PRIORITY_DEFAULT;
            
            if (this.mProfile != null) {
                string = getString(R.string.notifcation_title, getApplicationContext().getString(R.string.app_name), Build.MODEL);
            } else {
                string = getString(R.string.notifcation_title_notconnect);
            }
            
            builder.setOngoing(true)
                   .setSmallIcon(R.drawable.ic_connection_icon)
                   .setContentTitle(string)
                   .setContentText(str)
                   .setContentIntent(activity)
                   .setPriority(priority)
                   .setCategory(NotificationCompat.CATEGORY_SERVICE);
            
            startForeground(2, builder.build());
        }
    }

    private boolean runningOnAndroidTV() {
        return ((UiModeManager) getSystemService(UI_MODE_SERVICE)).getCurrentModeType() == 4;
    }

    private PendingIntent getGraphPendingIntent() {
        Class cls = mNotificationActivityClass != null ? mNotificationActivityClass : VPNActivityService.class;
        Intent intent = new Intent(this, cls);
        intent.putExtra("PAGE", "graph");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void allowAllAFFamilies(VpnService.Builder builder) {
        builder.addAddress("0.0.0.0", 0);
        builder.addAddress("::", 0);
    }

    private void addLocalNetworksToRoutes() {
        if (mLocalIP != null) {
            for (String str : this.mDnslist) {
                this.mRoutes.addIP(new CIDRIP(str, 32), true);
            }
            this.mRoutes.addIP(new CIDRIP(this.mRemoteGW, 32), true);
        }
    }

    private String getTunConfigString() {
        StringBuilder sb = new StringBuilder("TUNCFG UNQIUE STRING ips:");
        if (this.mLocalIP != null) sb.append(this.mLocalIP.toString());
        if (this.mLocalIPv6 != null) sb.append(this.mLocalIPv6);
        return sb.toString();
    }

    public ParcelFileDescriptor openTun() {
        VpnService.Builder builder = new VpnService.Builder();
        VpnStatus.logInfo(R.string.last_openvpn_tun_config);
        if (this.mProfile != null && this.mProfile.mAllowLocalLAN) {
            allowAllAFFamilies(builder);
        }
        
        if (this.mLocalIP == null && this.mLocalIPv6 == null) {
            VpnStatus.logError(getString(R.string.opentun_no_ipaddr));
            return null;
        }
        
        if (this.mLocalIP != null) {
            addLocalNetworksToRoutes();
            try {
                builder.addAddress(this.mLocalIP.mIp, this.mLocalIP.len);
            } catch (IllegalArgumentException e) {
                VpnStatus.logError(R.string.dns_add_error, this.mLocalIP, e.getLocalizedMessage());
                return null;
            }
        }
        
        if (this.mLocalIPv6 != null) {
            String[] split = this.mLocalIPv6.split("/");
            try {
                builder.addAddress(split[0], Integer.parseInt(split[1]));
            } catch (Exception e) {
                VpnStatus.logError(R.string.dns_add_error, this.mLocalIPv6, e.getLocalizedMessage());
            }
        }
        
        for (String str : this.mDnslist) {
            builder.addDnsServer(str);
        }
        
        if (this.mDomain != null) {
            builder.addSearchDomain(this.mDomain);
        }
        
        builder.setMtu(this.mMtu);
        
        for (NetworkSpace.ipAddress next : this.mRoutes.getNetworks(true)) {
            try {
                builder.addRoute(next.getIPv4Address(), next.networkMask);
            } catch (Exception e) {
                VpnStatus.logError(R.string.route_rejected, next.toString(), e.getLocalizedMessage());
            }
        }
        
        for (NetworkSpace.ipAddress next2 : this.mRoutesv6.getNetworks(true)) {
            try {
                builder.addRoute(next2.getIPv6Address(), next2.networkMask);
            } catch (Exception e) {
                VpnStatus.logError(R.string.route_rejected, next2.toString(), e.getLocalizedMessage());
            }
        }
        
        if (this.mProfile != null) {
            if (this.mProfile.mUseDefaultRoute) builder.addRoute("0.0.0.0", 0);
            if (this.mProfile.mUseDefaultRoutev6) builder.addRoute("::", 0);
            builder.setSession(this.mProfile.mName + (this.mProfile.mUseLzo ? " (LZO)" : ""));
        }
        
        builder.setConfigureIntent(getGraphPendingIntent());
        
        try {
            ParcelFileDescriptor establish = builder.establish();
            if (establish == null) VpnStatus.logError(R.string.tun_open_error);
            return establish;
        } catch (Exception e) {
            VpnStatus.logError(R.string.tun_open_error);
            VpnStatus.logError(e.getLocalizedMessage());
            return null;
        }
    }

    public void addDNS(String str) {
        this.mDnslist.add(str);
    }

    public void setDomain(String str) {
        this.mDomain = str;
    }

    public void addRoute(String str, String str2, String str3, String str4) {
        this.mRoutes.addIP(new CIDRIP(str, str2), true);
    }

    public void addRoutev6(String str, String str2) {
        this.mRoutesv6.addIP(new CIDRIP(str, 128), true);
    }

    public void setMtu(int i) {
        this.mMtu = i;
    }

    public void setLocalIP(CIDRIP cidrip) {
        this.mLocalIP = cidrip;
    }

    public void setLocalIP(String str, String str2, int i, String str3) {
        this.mLocalIP = new CIDRIP(str, i);
        this.mRemoteGW = str2;
        this.mMtu = i;
        this.mLastTunCfg = str3;
    }

    public void setLocalIPv6(String str) {
        this.mLocalIPv6 = str;
    }

    public String getTunReopenStatus() {
        return getTunConfigString().equals(this.mLastTunCfg) ? "NOACTION" : "OPEN_BEFORE_CLOSE";
    }

    public void requestInputFromUser(int i, String str) {
        VpnStatus.updateStateString("NEED", "need " + str, i, ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);
    }

    @Override
    public void updateState(String str, String str2, int i, ConnectionStatus connectionStatus) {
        if (this.mStarting && connectionStatus == ConnectionStatus.LEVEL_CONNECTED) {
            this.mStarting = false;
        }
        showNotification(str2, str, NOTIFICATION_CHANNEL_NEWSTATUS_ID, 0L, connectionStatus);
    }

    @Override
    public void updateByteCount(long j, long j2, long j3, long j4) {
        if (this.mDisplayBytecount) {
            String in = humanReadableByteCount(j3, true, getResources());
            String out = humanReadableByteCount(j4, true, getResources());
            showNotification(getResources().getString(R.string.statusline_bytecount, humanReadableByteCount(j, false, getResources()), in, humanReadableByteCount(j2, false, getResources()), out), "", NOTIFICATION_CHANNEL_BG_ID, this.mConnecttime, ConnectionStatus.LEVEL_CONNECTED);
        }
    }

    @Override // android.os.Handler.Callback
    public boolean handleMessage(Message message) {
        if (message.obj instanceof Runnable) {
            ((Runnable) message.obj).run();
        }
        return true;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        this.guiHandler = new Handler(this);
        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        if (intent != null && DISCONNECT_VPN.equals(intent.getAction())) {
            stopVPNInternal(false);
            return START_NOT_STICKY;
        }
        return super.onStartCommand(intent, i, i2);
    }

    @Override // android.app.Service
    public void onDestroy() {
        synchronized (this.mProcessLock) {
            if (this.mManagement != null) this.mManagement.stopVPN(true);
        }
        unregisterDeviceStateReceiver();
        VpnStatus.removeStateListener(this);
        VpnStatus.removeByteCountListener(this);
        super.onDestroy();
    }

    private void unregisterDeviceStateReceiver() {
        if (this.mDeviceStateReceiver != null) {
            try {
                unregisterReceiver(this.mDeviceStateReceiver);
            } catch (Exception ignored) {}
            this.mDeviceStateReceiver = null;
        }
    }

    public boolean stopVPNInternal(boolean z) {
        if (this.mManagement != null) return this.mManagement.stopVPN(z);
        return false;
    }

    @Override // de.blinkt.openvpn.core.IOpenVPNServiceInternal
    public void userPause(boolean z) {
        if (this.mManagement != null) {
            this.mManagement.pause(z ? OpenVPNManagement.pauseReason.userPause : OpenVPNManagement.pauseReason.noNetwork);
        }
    }

    @Override // de.blinkt.openvpn.core.IOpenVPNServiceInternal
    public boolean stopVPN(boolean z) {
        return stopVPNInternal(z);
    }

    @Override
    public boolean protect(int fd) {
        return super.protect(fd);
    }
}
