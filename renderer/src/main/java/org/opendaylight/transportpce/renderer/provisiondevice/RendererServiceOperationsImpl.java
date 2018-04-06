/*
 * Copyright © 2017 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.renderer.provisiondevice;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.transportpce.common.ResponseCodes;
import org.opendaylight.transportpce.common.Timeouts;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfacesImpl;
import org.opendaylight.transportpce.renderer.ModelMappingUtils;
import org.opendaylight.transportpce.renderer.NetworkModelWavelengthService;
import org.opendaylight.transportpce.renderer.ServicePathInputData;
import org.opendaylight.transportpce.renderer.provisiondevice.servicepath.ServicePathDirection;
import org.opendaylight.transportpce.renderer.provisiondevice.tasks.DeviceRenderingRollbackTask;
import org.opendaylight.transportpce.renderer.provisiondevice.tasks.DeviceRenderingTask;
import org.opendaylight.transportpce.renderer.provisiondevice.tasks.OlmPowerSetupRollbackTask;
import org.opendaylight.transportpce.renderer.provisiondevice.tasks.OlmPowerSetupTask;
import org.opendaylight.transportpce.renderer.provisiondevice.tasks.RollbackProcessor;
import org.opendaylight.yang.gen.v1.http.org.openroadm.pm.types.rev161014.PmGranularity;
import org.opendaylight.yang.gen.v1.http.org.openroadm.resource.types.rev161014.ResourceTypeEnum;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.service.types.rev170426.service.path.PathDescription;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev170426.ServiceDeleteInput;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev170426.ServiceDeleteOutput;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev170426.ServiceImplementationRequestInput;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev170426.ServiceImplementationRequestOutput;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev170426.ServicePathList;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev170426.service.path.list.ServicePaths;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev170426.service.path.list.ServicePathsKey;
import org.opendaylight.yang.gen.v1.http.org.transportpce.common.types.rev170907.olm.get.pm.input.ResourceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.http.org.transportpce.common.types.rev170907.olm.renderer.input.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.olm.rev170418.GetPmInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.olm.rev170418.GetPmOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.olm.rev170418.OlmService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.olm.rev170418.ServicePowerSetupInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.olm.rev170418.ServicePowerTurndownInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.olm.rev170418.ServicePowerTurndownOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.olm.rev170418.get.pm.output.Measurements;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RendererServiceOperationsImpl implements RendererServiceOperations {

    private static final Logger LOG = LoggerFactory.getLogger(RendererServiceOperationsImpl.class);
    private static final String FAILED = "Failed";
    private static final String OPERATION_FAILED = "Operation Failed";
    private static final String OPERATION_SUCCESSFUL = "Operation Successful";
    private static final int NUMBER_OF_THREADS = 4;

    private final DeviceRendererService deviceRenderer;
    private final OlmService olmService;
    private final DataBroker dataBroker;
    private ListeningExecutorService executor;
    private NetworkModelWavelengthService networkModelWavelengthService;

    public RendererServiceOperationsImpl(DeviceRendererService deviceRenderer, OlmService olmService,
            DataBroker dataBroker, NetworkModelWavelengthService networkModelWavelengthService) {
        this.deviceRenderer = deviceRenderer;
        this.olmService = olmService;
        this.dataBroker = dataBroker;
        this.networkModelWavelengthService = networkModelWavelengthService;
        this.executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(NUMBER_OF_THREADS));
    }

    @Override
    public ServiceImplementationRequestOutput serviceImplementation(ServiceImplementationRequestInput input) {
        LOG.info("Calling service impl request {} {}", input.getServiceName());
        RollbackProcessor rollbackProcessor = new RollbackProcessor();

        ServicePathInputData servicePathInputDataAtoZ
                = ModelMappingUtils.rendererCreateServiceInputAToZ(input.getServiceName(),
                        input.getPathDescription());
        ServicePathInputData servicePathInputDataZtoA
                = ModelMappingUtils.rendererCreateServiceInputZToA(input.getServiceName(),
                        input.getPathDescription());
        List<DeviceRenderingResult> renderingResults = deviceRendering(rollbackProcessor, servicePathInputDataAtoZ,
                servicePathInputDataZtoA);
        if (rollbackProcessor.rollbackAllIfNecessary() > 0) {
            return ModelMappingUtils.createServiceImplResponse(ResponseCodes.RESPONSE_FAILED, OPERATION_FAILED);
        }

        ServicePowerSetupInput olmPowerSetupInputAtoZ = ModelMappingUtils.createServicePowerSetupInput(
                renderingResults.get(0).getOlmList(), input);
        ServicePowerSetupInput olmPowerSetupInputZtoA = ModelMappingUtils.createServicePowerSetupInput(
                renderingResults.get(1).getOlmList(), input);
        olmPowerSetup(rollbackProcessor, olmPowerSetupInputAtoZ, olmPowerSetupInputZtoA);
        if (rollbackProcessor.rollbackAllIfNecessary() > 0) {
            return ModelMappingUtils.createServiceImplResponse(ResponseCodes.RESPONSE_FAILED, OPERATION_FAILED);
        }

        // run service activation test twice - once on source node and once on destination node
        List<Nodes> nodes = servicePathInputDataAtoZ.getServicePathInput().getNodes();
        Nodes sourceNode = nodes.get(0);
        Nodes destNode = nodes.get(nodes.size() - 1);

        String srcNetworkTp;
        String dstNetowrkTp;

        if (sourceNode.getDestTp().contains(OpenRoadmInterfacesImpl.NETWORK_TOKEN)) {
            srcNetworkTp = sourceNode.getDestTp();
        } else {
            srcNetworkTp = sourceNode.getSrcTp();
        }
        if (destNode.getDestTp().contains(OpenRoadmInterfacesImpl.NETWORK_TOKEN)) {
            dstNetowrkTp = destNode.getDestTp();
        } else {
            dstNetowrkTp = destNode.getSrcTp();
        }

        if (!isServiceActivated(sourceNode.getNodeId(), srcNetworkTp)
                || !isServiceActivated(destNode.getNodeId(), dstNetowrkTp)) {
            rollbackProcessor.rollbackAll();
            return ModelMappingUtils.createServiceImplResponse(ResponseCodes.RESPONSE_FAILED, OPERATION_FAILED);
        }

        //If Service activation is success update Network ModelMappingUtils
        this.networkModelWavelengthService.useWavelengths(input.getPathDescription());

        return ModelMappingUtils.createServiceImplResponse(ResponseCodes.RESPONSE_OK, OPERATION_SUCCESSFUL);
    }

    @Override
    public ServiceDeleteOutput serviceDelete(ServiceDeleteInput input) {
        String serviceName = input.getServiceName();

        // Obtain path description
        Optional<PathDescription> pathDescriptionOpt = getPathDescriptionFromDatastore(serviceName);
        PathDescription pathDescription;
        if (pathDescriptionOpt.isPresent()) {
            pathDescription = pathDescriptionOpt.get();
        } else {
            LOG.error("Unable to get path description for service {}!", serviceName);
            return ModelMappingUtils.createServiceDeleteResponse(ResponseCodes.RESPONSE_FAILED, OPERATION_FAILED);
        }

        ServicePathInputData servicePathInputDataAtoZ
                = ModelMappingUtils.rendererCreateServiceInputAToZ(serviceName, pathDescription);
        ServicePathInputData servicePathInputDataZtoA
                = ModelMappingUtils.rendererCreateServiceInputZToA(serviceName, pathDescription);

        // OLM turn down power
        try {
            LOG.debug("Turning down power on A-to-Z path");
            ServicePowerTurndownOutput atozPowerTurndownOutput = olmPowerTurndown(servicePathInputDataAtoZ);
            // TODO add some flag rather than string
            if (FAILED.equals(atozPowerTurndownOutput.getResult())) {
                LOG.error("Service power turndown failed on A-to-Z path for service {}!", serviceName);
                return ModelMappingUtils.createServiceDeleteResponse(ResponseCodes.RESPONSE_FAILED, OPERATION_FAILED);
            }

            LOG.debug("Turning down power on Z-to-A path");
            ServicePowerTurndownOutput ztoaPowerTurndownOutput = olmPowerTurndown(servicePathInputDataZtoA);
            // TODO add some flag rather than string
            if (FAILED.equals(ztoaPowerTurndownOutput.getResult())) {
                LOG.error("Service power turndown failed on Z-to-A path for service {}!", serviceName);
                return ModelMappingUtils.createServiceDeleteResponse(ResponseCodes.RESPONSE_FAILED, OPERATION_FAILED);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Error while turning down power!", e);
            return ModelMappingUtils.createServiceDeleteResponse(ResponseCodes.RESPONSE_FAILED, OPERATION_FAILED);
        }

        // delete service path with renderer
        LOG.debug("Deleting service path via renderer");
        this.deviceRenderer.deleteServicePath(servicePathInputDataAtoZ.getServicePathInput());
        this.deviceRenderer.deleteServicePath(servicePathInputDataZtoA.getServicePathInput());

        this.networkModelWavelengthService.freeWavelengths(pathDescription);

        return ModelMappingUtils.createServiceDeleteResponse(ResponseCodes.RESPONSE_OK, OPERATION_SUCCESSFUL);
    }

    private ServicePowerTurndownOutput olmPowerTurndown(ServicePathInputData servicePathInputData)
            throws InterruptedException, ExecutionException, TimeoutException {
        LOG.debug("Turning down power on A-to-Z path");
        Future<RpcResult<ServicePowerTurndownOutput>> powerTurndownFuture = this.olmService.servicePowerTurndown(
                new ServicePowerTurndownInputBuilder(servicePathInputData.getServicePathInput()).build());
        return powerTurndownFuture.get(Timeouts.DATASTORE_READ, TimeUnit.MILLISECONDS).getResult();
    }

    private Optional<PathDescription> getPathDescriptionFromDatastore(String serviceName) {
        InstanceIdentifier<PathDescription> pathDescriptionIID = InstanceIdentifier.create(ServicePathList.class)
                .child(ServicePaths.class, new ServicePathsKey(serviceName)).child(PathDescription.class);
        ReadOnlyTransaction pathDescReadTx = this.dataBroker.newReadOnlyTransaction();
        try {
            LOG.debug("Getting path description for service {}", serviceName);
            return pathDescReadTx.read(LogicalDatastoreType.OPERATIONAL, pathDescriptionIID)
                    .get(Timeouts.DATASTORE_READ, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.warn("Exception while getting path description from datastore {} for service {}!", pathDescriptionIID,
                    serviceName, e);
            return Optional.absent();
        }
    }

    private List<DeviceRenderingResult> deviceRendering(RollbackProcessor rollbackProcessor,
            ServicePathInputData servicePathDataAtoZ, ServicePathInputData servicePathDataZtoA) {
        LOG.info("Rendering devices A-Z");
        ListenableFuture<DeviceRenderingResult> atozrenderingFuture =
                this.executor.submit(new DeviceRenderingTask(this.deviceRenderer, servicePathDataAtoZ,
                        ServicePathDirection.A_TO_Z));

        LOG.info("Rendering devices Z-A");
        ListenableFuture<DeviceRenderingResult> ztoarenderingFuture =
                this.executor.submit(new DeviceRenderingTask(this.deviceRenderer, servicePathDataZtoA,
                        ServicePathDirection.Z_TO_A));
        ListenableFuture<List<DeviceRenderingResult>> renderingCombinedFuture =
                Futures.allAsList(atozrenderingFuture, ztoarenderingFuture);

        List<DeviceRenderingResult> renderingResults = new ArrayList<>(2);
        try {
            LOG.info("Waiting for A-Z and Z-A device renderers ...");
            renderingResults = renderingCombinedFuture.get(Timeouts.RENDERING_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.warn("Device rendering was not successful! Rendering will be rolled back.", e);
            //FIXME we can't do rollback here, because we don't have rendering results.
            //rollbackProcessor.addTask(new DeviceRenderingRollbackTask("AtoZDeviceTask", true));
            //rollbackProcessor.addTask(new DeviceRenderingRollbackTask("ZtoADeviceTask", true));
            return renderingResults;
        }

        rollbackProcessor.addTask(new DeviceRenderingRollbackTask("AtoZDeviceTask",
                ! renderingResults.get(0).isSuccess(), renderingResults.get(0).getRenderedNodeInterfaces(),
                this.deviceRenderer));
        rollbackProcessor.addTask(new DeviceRenderingRollbackTask("ZtoADeviceTask",
                ! renderingResults.get(1).isSuccess(), renderingResults.get(1).getRenderedNodeInterfaces(),
                this.deviceRenderer));
        return renderingResults;
    }

    private void olmPowerSetup(RollbackProcessor rollbackProcessor, ServicePowerSetupInput powerSetupInputAtoZ,
            ServicePowerSetupInput powerSetupInputZtoA) {
        LOG.info("Olm power setup A-Z");
        ListenableFuture<OLMRenderingResult> olmPowerSetupFutureAtoZ
                = this.executor.submit(new OlmPowerSetupTask(this.olmService, powerSetupInputAtoZ));

        LOG.info("OLM power setup Z-A");
        ListenableFuture<OLMRenderingResult> olmPowerSetupFutureZtoA
                = this.executor.submit(new OlmPowerSetupTask(this.olmService, powerSetupInputZtoA));
        ListenableFuture<List<OLMRenderingResult>> olmFutures =
                Futures.allAsList(olmPowerSetupFutureAtoZ, olmPowerSetupFutureZtoA);

        List<OLMRenderingResult> olmResults;
        try {
            LOG.info("Waiting for A-Z and Z-A OLM power setup ...");
            olmResults = olmFutures.get(Timeouts.OLM_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.warn("OLM power setup was not successful! Rendering and OLM will be rolled back.", e);
            rollbackProcessor.addTask(new OlmPowerSetupRollbackTask("AtoZOLMTask", true,
                    this.olmService, powerSetupInputAtoZ));
            rollbackProcessor.addTask(new OlmPowerSetupRollbackTask("ZtoAOLMTask", true,
                    this.olmService, powerSetupInputZtoA));
            return;
        }

        rollbackProcessor.addTask(new OlmPowerSetupRollbackTask("AtoZOLMTask", ! olmResults.get(0).isSuccess(),
                this.olmService, powerSetupInputAtoZ));
        rollbackProcessor.addTask(new OlmPowerSetupRollbackTask("ZtoAOLMTask", ! olmResults.get(1).isSuccess(),
                this.olmService, powerSetupInputZtoA));
    }

    private boolean isServiceActivated(String nodeId, String tpId) {
        LOG.info("Starting service activation test on node {} and tp {}", nodeId, tpId);
        for (int i = 0; i < 3; i++) {
            List<Measurements> measurements = getMeasurements(nodeId, tpId);
            if ((measurements != null) && verifyPreFecBer(measurements)) {
                return true;
            } else if (measurements == null) {
                LOG.warn("Device {} is not reporting PreFEC on TP: {}", nodeId, tpId);
                return true;
            } else {
                try {
                    Thread.sleep(Timeouts.SERVICE_ACTIVATION_TEST_RETRY_TIME);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        LOG.error("Service activation test failed on node {} and termination point {}!", nodeId, tpId);
        return false;
    }

    private List<Measurements> getMeasurements(String nodeId, String tp) {
        GetPmInputBuilder getPmIpBldr = new GetPmInputBuilder();
        getPmIpBldr.setNodeId(nodeId);
        getPmIpBldr.setGranularity(PmGranularity._15min);
        ResourceIdentifierBuilder rsrcBldr = new ResourceIdentifierBuilder();
        rsrcBldr.setResourceName(tp + "-OTU");
        getPmIpBldr.setResourceIdentifier(rsrcBldr.build());
        getPmIpBldr.setResourceType(ResourceTypeEnum.Interface);

        try {
            Future<RpcResult<GetPmOutput>> getPmFuture = this.olmService.getPm(getPmIpBldr.build());
            RpcResult<GetPmOutput> getPmRpcResult = getPmFuture.get();
            GetPmOutput getPmOutput = getPmRpcResult.getResult();
            if ((getPmOutput != null) && (getPmOutput.getNodeId() != null)) {
                LOG.info("successfully finished calling OLM's get PM");
                return getPmOutput.getMeasurements(); // may return null

            } else {
                LOG.warn("OLM's get PM failed for node {} and tp {}", nodeId, tp);
            }

        } catch (ExecutionException | InterruptedException e) {
            LOG.warn("Error occurred while getting PM for node {} and tp {}", nodeId, tp, e);
        }
        return null;
    }


    private boolean verifyPreFecBer(List<Measurements> measurements) {
        double preFecCorrectedErrors = Double.MIN_VALUE;
        double fecUncorrectableBlocks = Double.MIN_VALUE;

        for (Measurements measurement : measurements) {
            if (measurement.getPmparameterName().equals("preFECCorrectedErrors")) {
                preFecCorrectedErrors = Double.parseDouble(measurement.getPmparameterValue());
            }
            if (measurement.getPmparameterName().equals("FECUncorrectableBlocks")) {
                fecUncorrectableBlocks = Double.parseDouble(measurement.getPmparameterValue());
            }
        }

        LOG.info("Measurements: preFECCorrectedErrors = {}; FECUncorrectableBlocks = {}", preFecCorrectedErrors,
                fecUncorrectableBlocks);

        if (fecUncorrectableBlocks > Double.MIN_VALUE) {
            LOG.error("Data has uncorrectable errors, BER test failed");
            return false;
        } else {
            double numOfBitsPerSecond = 112000000000d;
            double threshold = 0.00002d;
            double result = preFecCorrectedErrors / numOfBitsPerSecond;
            LOG.info("PreFEC value is {}", Double.toString(result));
            return result <= threshold;
        }
    }

}
