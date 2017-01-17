package com.marklogic.mgmt.clusters;

import com.marklogic.mgmt.AbstractManager;
import com.marklogic.mgmt.ManageClient;
import com.marklogic.mgmt.admin.ActionRequiringRestart;
import com.marklogic.mgmt.admin.AdminConfig;
import com.marklogic.mgmt.admin.AdminManager;
import com.marklogic.rest.util.Fragment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class ClusterManager extends AbstractManager {

    private ManageClient manageClient;

    public ClusterManager(ManageClient client) {
        this.manageClient = client;
    }

    /**
     * This does not wait for the cluster to be available again; to do so, you'd need to use AdminManager.
     */
    public void restartLocalCluster() {
        manageClient.postJson("/manage/v2", "{\"operation\":\"restart-local-cluster\"}");
    }

    public void restartLocalCluster(AdminManager adminManager) {
        adminManager.invokeActionRequiringRestart(new ActionRequiringRestart() {
            @Override
            public boolean execute() {
                restartLocalCluster();
                return true;
            }
        });
    }

	public void modifyLocalCluster(final String payload, AdminManager adminManager) {
    	adminManager.invokeActionRequiringRestart(new ActionRequiringRestart() {
		    @Override
		    public boolean execute() {
		    	try {
				    putPayload(manageClient, "/manage/v2/properties", payload);
			    } catch (ResourceAccessException rae) {
				    /**
				     * This is odd. Plenty of other Manage endpoints cause ML to restart, but this one seems to trigger
				     * the restart before the PUT finishes. But - the PUT still seems to update the cluster properties
				     * correctly. So we catch this error and figure that we can wait for ML to restart like it normally
				     * would.
				     */
		    		if (logger.isInfoEnabled()) {
		    			logger.info("Ignoring somewhat expected error while updating local cluster properties: " + rae.getMessage());
				    }
			    }
			    return true;
		    }
	    });
	}

	public Fragment getLocalClusterProperties() {
    	return manageClient.getXml("/manage/v2/properties");
	}

    public String getVersion() {
    	Fragment f = manageClient.getXml("/manage/v2");
	    return f.getElementValue("/c:local-cluster-default/c:version");
    }

	/**
	 * Adds a host to the cluster represented by adminManager
	 * @param adminManager AdminManager for a host already in the cluster
	 * @param hostname hostname of the joining host
	 * @throws Exception
	 */
    public void addHost(AdminManager adminManager, String hostname) throws Exception {
		addHost(adminManager, hostname, "Default", null);
	}

	/**
	 * Adds a host to the cluster represented by adminManager
	 * @param adminManager AdminManager for a host already in the cluster
	 * @param hostname hostname of the joining host
	 * @param group name of the group within the cluster to add the joining host to
	 * @param zone zone information for the joining host (optional)
	 * @throws Exception
	 */
    public void addHost(AdminManager adminManager, String hostname, String group, String zone) throws Exception{
    	AdminConfig adminConfig = adminManager.getAdminConfig();

		AdminConfig joiningHostAdminConfig = new AdminConfig(hostname, 8001, adminConfig.getUsername(), adminConfig.getPassword());
		AdminManager joiningHostAdminManager = new AdminManager(joiningHostAdminConfig);

		// get the joining host's configuration
		Fragment fragment = joiningHostAdminManager.getServerConfig();

		// Make sure host has previously been initialized. If it hasn't, the host-id from the getServerConfig will be empty
		String hostId = fragment.getElementValue("node()/m:host-id");
		if (hostId.isEmpty()){
			throw new IllegalStateException("New host [" + hostname + "] has not been initialized. Please initialize the host first.");
		}

		// Send the joining host's config to the bootstrap host and receive
		// the cluster config data needed to complete the join.
		byte[] clusterConfigZipBytes = adminManager.postJoiningHostConfig(fragment, group, zone);
		if (clusterConfigZipBytes == null){
			throw new RuntimeException("Error sending new host's config to cluster and receiving updated cluster configuration");
		}

		// Final step of adding a host to the cluster. Post the zipped cluster config to the
		// joining host
		joiningHostAdminManager.postClustConfigToJoiningHost(clusterConfigZipBytes);
	}

	/**
	 * Removes the specified host from the cluster. Assumes connection information is of the host to remove
	 * @param hostname hostname of the server to remove from the cluster
	 */
	public void removeHost(String hostname){
		AdminConfig adminConfig = new AdminConfig(hostname, 8001, manageClient.getManageConfig().getUsername(), manageClient.getManageConfig().getPassword());
		AdminManager adminManager = new AdminManager(adminConfig);
		adminManager.leaveCluster();

	}

}
