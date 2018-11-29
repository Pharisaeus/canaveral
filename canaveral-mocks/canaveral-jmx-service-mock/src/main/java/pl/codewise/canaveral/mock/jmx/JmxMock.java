package pl.codewise.canaveral.mock.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JmxMock {

    private static final Logger log = LoggerFactory.getLogger(JmxMock.class);

    private List<JmxMockRule> rules;
    private JMXConnectorServer svr;
    private Registry rmiRegistry;
    private MBeanServer mBeanServer;

    public JmxMock(List<JmxMockRule> rules) {
        this.rules = rules;
    }

    public void start(String endpoint, int port)
            throws IOException, MalformedObjectNameException, NotCompliantMBeanException,
            InstanceAlreadyExistsException, MBeanRegistrationException {
        MBeanServer mBeanServer = createJmxConnectorServer(endpoint, port);

        Map<String, List<JmxMockRule>> rulesByMBean =
                rules.stream().collect(Collectors.groupingBy(JmxMockRule::getObjectName));

        for (Map.Entry<String, List<JmxMockRule>> ruleEntry : rulesByMBean.entrySet()) {
            mBeanServer.registerMBean(
                    new MockMBean(ruleEntry.getValue()),
                    new ObjectName(ruleEntry.getKey()));
        }
    }

    public void stop() throws IOException {
        for (JmxMockRule rule : rules) {
            try {
                mBeanServer.unregisterMBean(new ObjectName(rule.getObjectName()));
            } catch (Exception e) {
                log.warn("Failed to unregister mbean {}", rule.getObjectName());
            }
        }
        if (svr != null) {
            svr.stop();
        }
        if (rmiRegistry != null) {
            UnicastRemoteObject.unexportObject(rmiRegistry, true);
        }
    }

    private MBeanServer createJmxConnectorServer(String endpoint, int port) throws IOException {
        rmiRegistry = LocateRegistry.createRegistry(port);
        mBeanServer = ManagementFactory.getPlatformMBeanServer();
        JMXServiceURL url = new JMXServiceURL(endpoint);
        svr = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mBeanServer);
        svr.start();
        return mBeanServer;
    }
}
