# Fix Tailscale Certificate issue

```
> cd backend
> md certs

C:\path\to\chat-diet\backend>tailscale cert --cert-file certs/<machine>.<tailnet>.ts.net.crt --key-file certs/<machine>.<tailnet>.ts.net.key <machine>.<tailnet>.ts.net
Wrote public cert to certs/<machine>.<tailnet>.ts.net.crt
Wrote private key to certs/<machine>.<tailnet>.ts.net.key
```

```aiignore
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.0)

2026-08-23T19:25:04.960-04:00  INFO 28868 --- [backend] [           main] com.chatdiet.BackendApplication          : Starting BackendApplication using Java 21.0.9 with PID 28868 (C:\path\to\chat-diet\backend\build\classes\java\main started by <user> in C:\path\to\chat-diet\backend)
2026-08-23T19:25:04.971-04:00  INFO 28868 --- [backend] [           main] com.chatdiet.BackendApplication          : No active profile set, falling back to 1 default profile: "default"
2026-08-23T19:25:06.095-04:00  INFO 28868 --- [backend] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JDBC repositories in DEFAULT mode.
2026-08-23T19:25:06.254-04:00  INFO 28868 --- [backend] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 153 ms. Found 11 JDBC repository interfaces.
2026-08-23T19:25:06.736-04:00  WARN 28868 --- [backend] [           main] ConfigServletWebServerApplicationContext : Exception encountered during context initialization - cancelling refresh attempt: org.springframework.context.ApplicationContextException: Unable to start web server
2026-08-23T19:25:06.742-04:00  INFO 28868 --- [backend] [           main] .s.b.a.l.ConditionEvaluationReportLogger :

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
2026-08-23T19:25:06.757-04:00 ERROR 28868 --- [backend] [           main] o.s.boot.SpringApplication               : Application run failed

org.springframework.context.ApplicationContextException: Unable to start web server
        at org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext.onRefresh(ServletWebServerApplicationContext.java:167) ~[spring-boot-web-server-4.1.0.jar:4.1.0]
        at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:615) ~[spring-context-7.0.8.jar:7.0.8]
        at org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:143) ~[spring-boot-web-server-4.1.0.jar:4.1.0]
        at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:756) ~[spring-boot-4.1.0.jar:4.1.0]
        at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:445) ~[spring-boot-4.1.0.jar:4.1.0]
        at org.springframework.boot.SpringApplication.run(SpringApplication.java:321) ~[spring-boot-4.1.0.jar:4.1.0]
        at org.springframework.boot.SpringApplication.run(SpringApplication.java:1365) ~[spring-boot-4.1.0.jar:4.1.0]
        at org.springframework.boot.SpringApplication.run(SpringApplication.java:1354) ~[spring-boot-4.1.0.jar:4.1.0]
        at com.chatdiet.BackendApplication.main(BackendApplication.java:16) ~[main/:na]
Caused by: java.lang.IllegalStateException: Unable to create key store: Error reading certificate or key from file 'file:certs/<machine>.<tailnet>.ts.net.crt'
        at org.springframework.boot.ssl.pem.PemSslStoreBundle.createKeyStore(PemSslStoreBundle.java:108) ~[spring-boot-4.1.0.jar:4.1.0]
        at org.springframework.boot.ssl.pem.PemSslStoreBundle.lambda$new$0(PemSslStoreBundle.java:70) ~[spring-boot-4.1.0.jar:4.1.0]
        at org.springframework.util.function.SingletonSupplier.get(SingletonSupplier.java:113) ~[spring-core-7.0.8.jar:7.0.8]

```