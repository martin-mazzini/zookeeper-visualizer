package martinmazzini.zookeeper.management;

import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class ServiceRegistry implements Watcher {

    private ZooKeeperConnectionHelper zooKeeperHelper;
    private List<String> allServiceAddresses = null;
    private String currentZnode = null;
    private String serviceRegistryZnode;

    @Autowired
    AdressService adressService;

    public static ServiceRegistry of(ZooKeeperConnectionHelper zooKeeper, String serviceRegistryZnode) {
        ServiceRegistry serviceRegistry = new ServiceRegistry();
        serviceRegistry.zooKeeperHelper = zooKeeper;
        serviceRegistry.serviceRegistryZnode = serviceRegistryZnode;
        return serviceRegistry;
    }


    public void registerToCluster(String metadata) throws KeeperException, InterruptedException {
        if (currentZnode != null) {
            log.info("Already registered to service registry " + serviceRegistryZnode + " with adress " + metadata);
            return;
        }
        this.currentZnode = zooKeeperHelper.getZooKeeper().create(serviceRegistryZnode + "/n_", metadata.getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        log.info("Node registered to service registry " + serviceRegistryZnode + " with address " + metadata);
    }

    public void registerForUpdates() {
        try {
            updateAddresses();
        } catch (KeeperException e) {
        } catch (InterruptedException e) {
        }
    }

    public void unregisterFromCluster() {
        try {
            if (currentZnode != null && zooKeeperHelper.getZooKeeper().exists(currentZnode, false) != null) {
                zooKeeperHelper.getZooKeeper().delete(currentZnode, -1);
                log.info("Node unregistered from registry " + serviceRegistryZnode + " : " + adressService.getNodeAdress());
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    void createServiceRegistryNode() {
        try {
            if (zooKeeperHelper.getZooKeeper().exists(serviceRegistryZnode, false) == null) {
                zooKeeperHelper.getZooKeeper().create(serviceRegistryZnode, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    synchronized List<String> getAllServiceAddresses() throws KeeperException, InterruptedException {
        if (allServiceAddresses == null) {
            updateAddresses();
        }
        return allServiceAddresses;
    }

    private synchronized void updateAddresses() throws KeeperException, InterruptedException {
        List<String> znodes = zooKeeperHelper.getZooKeeper().getChildren(serviceRegistryZnode, this);

        List<String> addresses = new ArrayList<>(znodes.size());

        for (String znode : znodes) {
            //TODO RACE COND
            String serviceFullpath = serviceRegistryZnode + "/" + znode;
            Stat stat = zooKeeperHelper.getZooKeeper().exists(serviceFullpath, false);
            if (stat == null) {
                continue;
            }
            //TODO watch false?
            byte[] addressBytes = zooKeeperHelper.getZooKeeper().getData(serviceFullpath, false, stat);
            String address = new String(addressBytes);
            addresses.add(address);
        }

        this.allServiceAddresses = Collections.unmodifiableList(addresses);
        log.info("Updating addresses in service registry + " + serviceRegistryZnode + ". The cluster addresses are: " + this.allServiceAddresses);
    }

    @Override
    public void process(WatchedEvent event) {
        try {
            log.info("Event triggered in Service Registry " + serviceRegistryZnode + " : " + event.getType());
            updateAddresses();
        } catch (KeeperException e) {
        } catch (InterruptedException e) {
        }
    }
}
