package embedded.keycloak;

import javax.naming.CompositeName;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingException;
import javax.naming.spi.NamingManager;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.jboss.resteasy.core.Dispatcher;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.managers.ApplianceBootstrap;
import org.keycloak.services.resources.KeycloakApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Created by tom on 12.06.16.
 */
public class EmbeddedKeycloakApplication extends KeycloakApplication {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedKeycloakApplication.class);

    static KeycloakServerProperties keycloakServerProperties;

    public EmbeddedKeycloakApplication(@Context ServletContext context, @Context Dispatcher dispatcher) throws NamingException {

        super(augmentToRedirectContextPath(context), dispatcher);

        tryCreateMasterRealmAdminUser();
    }

    private void tryCreateMasterRealmAdminUser() {

        KeycloakSession session = getSessionFactory().create();

        ApplianceBootstrap applianceBootstrap = new ApplianceBootstrap(session);

        KeycloakServerProperties.AdminUser admin = keycloakServerProperties.getAdminUser();

        try {
            session.getTransactionManager().begin();
            applianceBootstrap.createMasterRealmUser(admin.getUsername(), admin.getPassword());
            session.getTransactionManager().commit();
        } catch (Exception ex) {
            LOG.warn("Couldn't create keycloak master admin user: {}", ex.getMessage());
            session.getTransactionManager().rollback();
        }

        session.close();
    }


    private static ServletContext augmentToRedirectContextPath(ServletContext servletContext) throws NamingException {

        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:./target/keycloak");
        jdbcDataSource.setUser("sa");
        mockJndiEnvironment(jdbcDataSource);
        keycloakServerProperties = new KeycloakServerProperties();
        ClassLoader classLoader = servletContext.getClassLoader();
        Class[] interfaces = {ServletContext.class};

        InvocationHandler invocationHandler = (proxy, method, args) -> {

            if ("getContextPath".equals(method.getName())) {
                return keycloakServerProperties.getContextPath();
            }

            if ("getInitParameter".equals(method.getName()) && args.length == 1 && "keycloak.embedded".equals(args[0])) {
                return "true";
            }

            LOG.info("{} {}", method.getName(), Arrays.toString(args));

            return method.invoke(servletContext, args);
        };

        return ServletContext.class.cast(Proxy.newProxyInstance(classLoader, interfaces, invocationHandler));
    }

    public static void mockJndiEnvironment(DataSource dataSource) throws NamingException {
        NamingManager.setInitialContextFactoryBuilder((env) -> (environment) -> new InitialContext() {

            @Override
            public Object lookup(Name name) throws NamingException {
                return lookup(name.toString());
            }

            @Override
            public Object lookup(String name) throws NamingException {

                if ("spring/datasource".equals(name)) {
                    return dataSource;
                }

                return null;
            }

            @Override
            public NameParser getNameParser(String name) throws NamingException {
                return CompositeName::new;
            }

            @Override
            public void close() throws NamingException {
                //NOOP
            }
        });
    }
}
