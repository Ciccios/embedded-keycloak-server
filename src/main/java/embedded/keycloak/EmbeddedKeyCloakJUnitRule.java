package embedded.keycloak;

import static org.awaitility.Awaitility.await;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.h2.tools.Server;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.keycloak.services.filters.KeycloakSessionServletFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedKeyCloakJUnitRule extends ExternalResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedKeyCloakJUnitRule.class);

    private TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Tomcat tomcat;
    private static volatile String tomcatEventType;
    private Server h2Db;

    @Override
    protected void before() throws SQLException, IOException, LifecycleException {
        temporaryFolder.create();
        h2Db = Server.createTcpServer().start();

        tomcat = new Tomcat();
        tomcat.setPort(findAvailableEphemeralPort());
        tomcat.setBaseDir(temporaryFolder.getRoot().getAbsolutePath());

        Context context = tomcat.addContext("", new File(".").getAbsolutePath());

        String servletName = "KeyCloak";

        addKeyCloakApplicationServlet(context, servletName);
        addKeyCloakFilter(context);

        context.addServletMappingDecoded("/*", servletName);

        tomcat.getServer().addLifecycleListener(event -> tomcatEventType = event.getType());
        tomcat.start();
        //Wait until tomcat has fully started
        await().until(() -> tomcatEventType.equals("after_start"));
        LOG.info("Tomcat has successfully started");
    }

    @Override
    protected void after() {
        stopAndDestroyTomcat();
        stopH2Db();
        temporaryFolder.delete();
    }

    private void stopH2Db() {
        h2Db.stop();
        await().until(()-> !h2Db.isRunning(false));
        LOG.info("H2 DB has stopped");
    }

    public int getTomcat() {
        return tomcat.getServer().getPort();
    }

    private void stopAndDestroyTomcat() {
        try {
            tomcat.stop();
            await().until(() -> tomcatEventType.equals("after_stop"));
            tomcat.destroy();
            await().until(() -> tomcatEventType.equals("after_destroy"));
        } catch (LifecycleException e) {
            System.out.println("Exception Caught!");
        }
        LOG.info("Tomcat Stopped and Destroyed");
    }

    private static void addKeyCloakFilter(Context context) {
        FilterDef filterDef = new FilterDef();
        filterDef.setFilter(new KeycloakSessionServletFilter());
        filterDef.setFilterName(KeycloakSessionServletFilter.class.getName());
        context.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.addURLPattern("/*");
        filterMap.setFilterName(KeycloakSessionServletFilter.class.getName());

        context.addFilterMap(filterMap);
    }

    private static void addKeyCloakApplicationServlet(Context context, String servletName) {
        Wrapper wrapper = context.createWrapper();
        wrapper.setLoadOnStartup(0);
        wrapper.setName(servletName);
        wrapper.setServlet(new HttpServlet30Dispatcher());
        wrapper.addInitParameter("javax.ws.rs.Application", EmbeddedKeycloakApplication.class.getName());
        wrapper.addInitParameter(ResteasyContextParameters.RESTEASY_SERVLET_MAPPING_PREFIX, "/auth");
        wrapper.addInitParameter(ResteasyContextParameters.RESTEASY_USE_CONTAINER_FORM_PARAMS, "true");
        context.addChild(wrapper);
    }

    private static int findAvailableEphemeralPort() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        int localPort = serverSocket.getLocalPort();
        LOG.info("Starting Embedded Tomcat on port:" + localPort);
        serverSocket.close();
        return localPort;
    }
}
