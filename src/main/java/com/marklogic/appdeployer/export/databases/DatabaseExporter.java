package com.marklogic.appdeployer.export.databases;

import com.marklogic.appdeployer.ConfigDir;
import com.marklogic.appdeployer.export.AbstractNamedResourceExporter;
import com.marklogic.appdeployer.export.ExportedResources;
import com.marklogic.appdeployer.export.forests.ForestExporter;
import com.marklogic.mgmt.ManageClient;
import com.marklogic.mgmt.ResourceManager;
import com.marklogic.mgmt.databases.DatabaseManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * After the database is written, we want to find its forests and write them out as well, including the host name.
 * We'll offer an option in the future to remove the host.
 * <p>
 * This is rough - the database is exported with forest names, and a forest is exported with its database name. Can't
 * do both. Since we already deploy a database first and then deploy custom forests, we'll stick with that order and
 * drop the forest names from the database.
 */
public class DatabaseExporter extends AbstractNamedResourceExporter {

	private boolean exportForests = true;

	public DatabaseExporter(ManageClient manageClient, String... databaseNames) {
		super(manageClient, databaseNames);
	}

	@Override
	protected ResourceManager newResourceManager(ManageClient manageClient) {
		return new DatabaseManager(manageClient);
	}

	@Override
	protected File getResourceDirectory(File baseDir) {
		return new ConfigDir(baseDir).getDatabasesDir();
	}

	@Override
	protected String beforeResourceWrittenToFile(String resourceName, String payload) {
		return removeForestsSoDatabaseCanBeCreatedBeforeForestsAre(payload);
	}

	/**
	 * Currently only supports JSON.
	 *
	 * @param payload
	 * @return
	 */
	protected String removeForestsSoDatabaseCanBeCreatedBeforeForestsAre(String payload) {
		return removeJsonKeyFromPayload(payload, "forest");
	}

	@Override
	protected String[] getExportMessages() {
		return new String[]{"The 'forest' key was removed from each exported database so that databases can be deployed before forests."};
	}

	@Override
	public ExportedResources exportResources(File baseDir) {
		ExportedResources resources = super.exportResources(baseDir);
		if (isExportForests()) {
			resources = exportForests(baseDir, resources);
		}
		return resources;
	}

	protected ExportedResources exportForests(File baseDir, ExportedResources resources) {
		DatabaseManager dbMgr = new DatabaseManager(getManageClient());
		for (String dbName : getResourceNames()) {
			List<String> forestNames = dbMgr.getForestNames(dbName);
			ForestExporter forestExporter = new ForestExporter(dbName, getManageClient(), forestNames.toArray(new String[]{}));
			resources.add(forestExporter.exportResources(baseDir));
		}
		return resources;
	}

	public boolean isExportForests() {
		return exportForests;
	}

	public void setExportForests(boolean exportForests) {
		this.exportForests = exportForests;
	}
}
