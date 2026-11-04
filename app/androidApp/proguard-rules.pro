# jsch makes its ciphers, key exchanges and signatures from the class names in its configuration
-keep class com.jcraft.jsch.** { *; }

# what jsch can work with where it is there and the app does not carry: Pageant over JNA, Kerberos, Unix sockets and
# the loggers
-dontwarn com.sun.jna.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
