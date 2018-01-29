/*
 * Copyright © 2017 Orange, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.transportpce.stubpce.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.stubpce.rev170426.StubpceListener;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.stubpce.rev170426.StubpceService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to register
 * Stubpce Service and Notification.
 * @author <a href="mailto:martial.coulibaly@gfi.com">Martial Coulibaly</a> on behalf of Orange
 *
 */
public class StubpceProvider {
    private static final Logger LOG = LoggerFactory.getLogger(StubpceProvider.class);
    private final RpcProviderRegistry rpcRegistry;
    private final NotificationPublishService notificationPublishService;
    private final DataBroker dataBroker;


    private BindingAwareBroker.RpcRegistration<StubpceService> rpcRegistration;
    private ListenerRegistration<StubpceListener> stubPcelistenerRegistration;

    public StubpceProvider(RpcProviderRegistry rpcProviderRegistry,final DataBroker dataBroker,
            NotificationService notificationService, NotificationPublishService notificationPublishService) {
        this.rpcRegistry = rpcProviderRegistry;
        this.notificationPublishService = notificationPublishService;
        this.dataBroker = dataBroker;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("StubpceProvider Session Initiated");
        final StubpceImpl consumer = new StubpceImpl(notificationPublishService,dataBroker);
        rpcRegistration = rpcRegistry.addRpcImplementation(StubpceService.class, consumer);
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("StubpceProvider Closed");
        rpcRegistration.close();
        stubPcelistenerRegistration.close();
    }
}
