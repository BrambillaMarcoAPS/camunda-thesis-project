package com.example.camunda;

import io.camunda.zeebe.client.ZeebeClient;

public class ZeebeConfig {

    private static final String Clusterurl= "https://2f60dd20-8d06-4144-be99-bd4fac69f080.syd-1.zeebe.camunda.io:443";
    private static final String CLUSTER_ID = "2f60dd20-8d06-4144-be99-bd4fac69f080";
    private static final String CLIENT_ID = "lf9bCWJaiFoc6vkVi8xq63fVls6~Py_G";
    private static final String CLIENT_SECRET = "UUJj_qPM2Ea7-j2uRN1Xm.CeaQufp_rSdaimUn8LwSm2~s.RO~D67_IiZtrLIhaV";
    private static final String REGION = "syd-1";

    public static ZeebeClient createZeebeClient() {
        return ZeebeClient.newCloudClientBuilder()
                .withClusterId(CLUSTER_ID)
                .withClientId(CLIENT_ID)
                .withClientSecret(CLIENT_SECRET)
                .withRegion(REGION)
                .build();
    }
}
