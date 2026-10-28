package personal.wallpaper.os4.e1;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import java.util.Base64;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowSigningInfo;
import personal.wallpaper.protocol.lock.LockProtocol;

/** 已核77/68 APK的公开证书，不含私钥；只供真实PM鉴别宿主测试。 */
public final class NativeSigningFixtures {
    private NativeSigningFixtures(){ }
    public static void host(Context context,int uid){install(context,LockProtocol.HOST_PACKAGE,HOST,uid);}
    public static void module(Context context,int uid){install(context,LockProtocol.MODULE_PACKAGE,MODULE,uid);}
    private static void install(Context context,String name,String certificate,int uid){
        PackageInfo pkg=new PackageInfo();pkg.packageName=name;pkg.applicationInfo=new ApplicationInfo();pkg.applicationInfo.packageName=name;pkg.applicationInfo.uid=uid;pkg.applicationInfo.enabled=true;
        pkg.signingInfo=new SigningInfo();Shadow.<ShadowSigningInfo>extract(pkg.signingInfo).setSignatures(new Signature[]{new Signature(Base64.getDecoder().decode(certificate.replaceAll("\\s", "")))});
        ShadowPackageManager packages=Shadow.extract(context.getPackageManager());packages.installPackage(pkg);packages.setPackagesForUid(uid,name);
    }
    private static final String HOST="""
        MIIENzCCAp+gAwIBAgIIX8T3vAoEwP0wDQYJKoZIhvcNAQELBQAwSTELMAkGA1UE
        BhMCQ04xETAPBgNVBAoTCFBlcnNvbmFsMScwJQYDVQQDEx5PUzQgV2FsbHBhcGVy
        IFBlcnNvbmFsIFJlbGVhc2UwIBcNMjYxMDE2MDYwNzQyWhgPMjA1NDAzMDMwNjA3
        NDJaMEkxCzAJBgNVBAYTAkNOMREwDwYDVQQKEwhQZXJzb25hbDEnMCUGA1UEAxMe
        T1M0IFdhbGxwYXBlciBQZXJzb25hbCBSZWxlYXNlMIIBojANBgkqhkiG9w0BAQEF
        AAOCAY8AMIIBigKCAYEAnRr5CKioA/6TYhocsBhF53GMT0KEzaP5Xxatr9P1vN8l
        HTdlrHmC9RUTcal+49lhVg+YxYTCOklTCuDDFbpV+R3IiImrxRDxhoZf6v/M8wsI
        buOg2abLZjoEyNcCuJ2NdT3EkVRW859pqWOW7Hx+db6X0ZAIuyiY29r45Hsw+1of
        Zz4Fh7ctALrUmGBSUgsY+QF97R7kC8MIIZlQHEnnOx0zGrlWQR9oOyIpHqcPqxz3
        MOO6N18QZlFbNaij/kbKSoLIlVoepvO61Uo+tgtygsxuaNH+q3XcMiBJELVOaZx9
        aM+WlPypLhMui6mWpX5YTd30RgirepisZzViQxNIzNbM0RSXwSedmRgb5km+xJPL
        PkFqFTVBZvMo0pDdJTX7PhOxZ9RvTST51wbOzV1wy7R1OnJH3J3EF72HjyGIVHLr
        qgpOL3RwDteQNd5UqFJiFgLZFsTHljNdFQKFFWZZvjSJTy72TBXPyD97XF1SJl4z
        t3mWlc9nhr1l6xwGZ7nlAgMBAAGjITAfMB0GA1UdDgQWBBRk4lQOTEec6aAnoLr/
        S3FI3cXvQzANBgkqhkiG9w0BAQsFAAOCAYEAOrEcNH0tdFntVYc/C6BkRxUXi5bh
        M7xHC9cb4BwptyoWhuhWpY3LCcVMaSawgd8W9LrgiIWZJuES4MxiCxOcdryodtWm
        /HPywOpJmXYV5Ua0Isu9z3zzkRropalDMRpWJx+cJEcw7G4dAPNDEJP+ETTMfS7r
        hRHXB5m1UhAt4/5Kob2125H46OvYTbKvXhJyCiE2iqTqKles7NQXL8TYYtW0glCm
        CSF4J4bMyGo1jSRHShad3qvidPdK4KvgcW1LSEjB336qOzfxg6/ce+pDp3y5GlL9
        CfA1DByZAyiVc9SRnFQ2XZHPpD/QEkvlYZIImlXG8NMaRmk0Ze0o211MLX1g+h2o
        3etHGMdCixrMipyqNsnOm0GRz66X1Dg67ApH+4ytHjlm5bK2I+TojPu46CAaLbqj
        UA8N8yddEsosSkmVHsMf54O82ZTxvPfc1XC/ZowAY7k+DrtznvUl81EpiMaQ8XrE
        yX4x81ykXeYfdLmI4C99kqlQ71QBNo+YWWFa
        """;
    private static final String MODULE="""
        MIIEOzCCAqOgAwIBAgIIHtSHNFvjBQowDQYJKoZIhvcNAQELBQAwSzELMAkGA1UE
        BhMCQ04xETAPBgNVBAoTCFBlcnNvbmFsMSkwJwYDVQQDEyBPUzQgSG9tZSBDb21w
        YXQgUGVyc29uYWwgUmVsZWFzZTAgFw0yNjEwMTYwNjA3NDRaGA8yMDU0MDMwMzA2
        MDc0NFowSzELMAkGA1UEBhMCQ04xETAPBgNVBAoTCFBlcnNvbmFsMSkwJwYDVQQD
        EyBPUzQgSG9tZSBDb21wYXQgUGVyc29uYWwgUmVsZWFzZTCCAaIwDQYJKoZIhvcN
        AQEBBQADggGPADCCAYoCggGBAK5hmj95Cruek8ZmQ9caklzPxa728ChJ30r3qASw
        up6QXOG3CknaxhUwy4amqyvI2iM6eIhkptcRe3Kmcu9G6/55fHQInLYjlKBevjjE
        fZy7zns44h4YIe6D8R2rQKAEVtZGZoKUFdti4Er8EswjF60n8ibD5CQ+e6wlJiRe
        qEnpX4zFEhJLkdrMkAKdBU4b1VC/bzpuyVCL4zls672ytbn86oIwq8B8P9yLr3HR
        nfJAXOc2dBNE1imQNcyui/Em3s3Sf24lw2eR4K/dHdy57qRRjuUo8TrcGDxddznM
        MOqTyJaF+MpPEIbTOt26ptWAU6D5x1I8kWbf0hmw6ODfV6DttUlANCUddH2ExIu+
        4oUCt6MPJR2utt8GLxIi26EjV/1SKOZF6sjoRuGFtcWr9tCOVhZPW+IGoniWEL8i
        wWEvJnJDZIjFg9BW093kkQps9fLqof7573PzT3kYJi2yNSXnSqLvmz2stMAJ0IEP
        cJ4cAtI38sbuHjMKk0POc/0OiQIDAQABoyEwHzAdBgNVHQ4EFgQU3YBO/K0WqrNP
        98vyMyvOxrHpgkgwDQYJKoZIhvcNAQELBQADggGBACC61ppCjXFaqD1huBT2J7Hw
        fZ6Q1klGshx9RBA5Dln8jmkt1dJX5PjcYRtwywWwS5RuVwrIH8wLJ0YxJdO0rae3
        5t5f3NwDmwq3Rt8GX6JHw1FlwqH6IXoGQCmiKI4SuqKBHx5torXWkfbOrANiyAYx
        p5IeA1vO8nthFtsXaLIJ6GvOCa1YSIumqKVMRY2FJc/MMp20MqUzdejsFBU+sYqr
        ULMNdMSFf65BbItS1jdLGyS2cg/xBjQbOi8b9sreHTrwPI2Q4gzuKFW8OF4Yz3xO
        LA4NZX/EpZ6JsOJOBesQWGUr/81PnxMR7Wk2FcFGwXacOToutdw+pL5Y9/fBepaQ
        aD4vo2lIxQ8rAkJpOJFV6xA4wnaucAS9vsGfRkkBrT1vpPkTt8xrCrGhpOfuUVjA
        Sk/7Wb20i+Vcr8vlT9zUPFj4msqU7eTtUYkxH61RU0OeHF54fpheHIpiBDj/gLWp
        21dzh7YONFeplqAVebfD4uVcLFWFQVqBLt8+XA05JA==
        """;
}
