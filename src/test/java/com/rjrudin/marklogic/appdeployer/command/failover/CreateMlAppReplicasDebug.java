package com.rjrudin.marklogic.appdeployer.command.failover;

import com.rjrudin.marklogic.appdeployer.AppConfig;
import com.rjrudin.marklogic.appdeployer.command.CommandContext;
import com.rjrudin.marklogic.mgmt.ManageClient;

/**
 * Not an actual test, as this depends on an environment with multiple hosts, which is normally not the case on a
 * development machine.
 */
public class CreateMlAppReplicasDebug {

    public static void main(String[] args) {
        ManageClient manageClient = new ManageClient();
        AppConfig appConfig = new AppConfig();
        CommandContext context = new CommandContext(appConfig, manageClient, null);

        CreateMlAppReplicasCommand command = new CreateMlAppReplicasCommand();
        command.execute(context);
    }
}