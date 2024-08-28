/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.itpf.datalayer.dps.persistence.PersistenceObject;
import com.ericsson.oss.pmic.dto.Entity;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.CellTrafficSubscription;
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.pmic.dto.subscription.EventSubscription;
import com.ericsson.oss.pmic.dto.subscription.GpehSubscription;
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.UetrSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.CriteriaSpecification;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.NodeInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.TriggerEventInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.EbsOutputStrategy;
import com.ericsson.oss.pmic.dto.subscription.enums.NodeFilter;
import com.ericsson.oss.pmic.dto.subscription.enums.NodeGrouping;
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.TraceDepth;
import com.ericsson.oss.pmic.dto.subscription.enums.UeType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;

public class SubscriptionCreatorHelper {

    private static final Date now = new Date();
    private static final Date tomorrow = new Date(now.getTime() + TimeUnit.DAYS.toMillis(1));

    public void populateMoInstanceSubscriptionDTO(final MoinstanceSubscription subscription) {
        populateStatisticalSubscriptionDTO(subscription);
        final List<MoinstanceInfo> list = new ArrayList<>();
        list.add(new MoinstanceInfo("moInstanceNodeName", "moInstanceGroup"));
        list.add(new MoinstanceInfo("moInstanceNodeName1", "moInstanceGroup1"));
        subscription.setMoInstances(list);
    }

    public void populateMoInstanceSubscriptionDTOWithManyCountersAndMoInstances(final MoinstanceSubscription subscription) {
        populateStatisticalSubscriptionDTOWithManyCounters(subscription);
        final List<MoinstanceInfo> counters = new ArrayList<>();
        for (int i = 0; i < 8000; i++) {
            counters.add(new MoinstanceInfo("Some long MoInstance name" + i, "Some long MoInstance group" + i % 10));
        }
        subscription.setMoInstances(counters);
    }

    public void populateSubscriptionDTO(Subscription sub) {
        sub.setName("HelloWorld");
        sub.setNextVersionName("155.33.69");
        sub.setPrevVersionName("1.0.0");
        sub.setOwner("Mister Nice");
        sub.setDescription("Monitoring nodes.");
        sub.setUserType(UserType.USER_DEF);
        sub.setAdministrationState(AdministrationState.INACTIVE);
        sub.setOperationalState(OperationalState.NA);
        sub.setTaskStatus(TaskStatus.OK);
        sub.setActivationTime(now);
        sub.setDeactivationTime(tomorrow);
        sub.setRop(RopPeriod.FIFTEEN_MIN);
        sub.setUserActivationDateTime(now);
        sub.setUserDeActivationDateTime(tomorrow);
        sub.setPersistenceTime(now);
        sub.setScheduleInfo(new ScheduleInfo(now, tomorrow));
        sub.setType(SubscriptionType.fromSubscriptionClass(sub.getClass()));
    }

    public void populateResourceSubscriptionDTO(ResourceSubscription sub) {
        populateSubscriptionDTO(sub);
        sub.setPnpEnabled(true);
        sub.setFilterOnManagedElement(true);
        sub.setFilterOnManagedFunction(true);
        sub.setNodeListIdentity(123456789);
        sub.setSelectedNeTypes(new ArrayList<String>(Collections.singletonList("ERBS")));
        sub.setNodeFilter(NodeFilter.NODE_FUNCTION);
        sub.setCbs(true);
        sub.setCriteriaSpecification(Collections.singletonList(new CriteriaSpecification("name", "123")));
    }

    public void populateEventSubscriptionDTO(EventSubscription sub) {
        populateResourceSubscriptionDTO(sub);
        sub.setEvents(Collections.singletonList(new EventInfo("name", "group")));
        sub.setOutputMode(OutputModeType.STREAMING);
        sub.setStreamInfoList(Collections.singletonList(new StreamInfo("192.168.0.42", 4554, 2)));
    }

    public void populateEventSubscriptionDTOWithManyEvents(EventSubscription sub) {
        populateResourceSubscriptionDTO(sub);
        final List<EventInfo> events = new ArrayList<>();
        for (int i = 0; i < 8000; i++) {
            events.add(new EventInfo("Some long event name" + i, "Some long event group" + i % 10));
        }
        sub.setEvents(events);
        sub.setOutputMode(OutputModeType.STREAMING);
        sub.setStreamInfoList(Collections.singletonList(new StreamInfo("192.168.0.42", 4554, 2)));
    }

    public void populateCellTraceSubscriptionDTO(CellTraceSubscription sub) {
        populateEventSubscriptionDTO(sub);
        sub.setAsnEnabled(true);
        sub.setUeFraction(123);
        sub.setEbsCounters(Collections.singletonList(new CounterInfo("counterInfoName", "INTERNAL")));
    }

    public void populateCellTraceSubscriptionDTOWithManyEventsAndEbsCounters(CellTraceSubscription sub) {
        populateEventSubscriptionDTOWithManyEvents(sub);
        sub.setAsnEnabled(true);
        sub.setUeFraction(123);
        final List<CounterInfo> counters = new ArrayList<>();
        for (int i = 0; i < 8000; i++) {
            counters.add(new CounterInfo("Some long counter name" + i, "Some long counter group" + i % 10));
        }
        sub.setEbsCounters(counters);
    }

    public void populateEbmSubscriptionDTO(EbmSubscription sub) {
        populateEventSubscriptionDTO(sub);
        sub.setCompressionEnabled(true);
        sub.setEbsOutputStrategy(EbsOutputStrategy.TGPP);
        sub.setEbsEnabled(true);
        sub.setEbsOutputInterval(RopPeriod.FIFTEEN_MIN);
        sub.setEbsCounters(Collections.singletonList(new CounterInfo("counterInfoName", "INTERNAL")));
    }

    public void populateEbmSubscriptionDTOWithManyEbsCountersAndEvents(EbmSubscription sub) {
        populateEventSubscriptionDTOWithManyEvents(sub);
        sub.setCompressionEnabled(true);
        sub.setEbsOutputStrategy(EbsOutputStrategy.TGPP);
        sub.setEbsEnabled(true);
        sub.setEbsOutputInterval(RopPeriod.FIFTEEN_MIN);
        final List<CounterInfo> counters = new ArrayList<>();
        for (int i = 0; i < 8000; i++) {
            counters.add(new CounterInfo("Some long counter name" + i, "Some long counter group" + i % 10));
        }
        sub.setEbsCounters(counters);
    }

    public void populateStatisticalSubscriptionDTO(StatisticalSubscription sub) {
        populateResourceSubscriptionDTO(sub);
        sub.setCounters(Collections.singletonList(new CounterInfo("counterName", "counterGroup")));
    }

    public void populateStatisticalSubscriptionDTOWithManyCounters(StatisticalSubscription sub) {
        populateResourceSubscriptionDTO(sub);
        final List<CounterInfo> counters = new ArrayList<>();
        for (int i = 0; i < 8000; i++) {
            counters.add(new CounterInfo("Some long counter name" + i, "Some long counter group" + i % 10));
        }
        sub.setCounters(counters);
    }

    public void populateUeTraceSubscriptionDTO(final UETraceSubscription sub) {
        populateSubscriptionDTO(sub);
        sub.setOutputMode(OutputModeType.FILE);
        sub.setStreamInfo(new StreamInfo("192.168.0.42", 4554, 2));
        sub.setTraceReference("123");
        sub.setUeInfo(new UeInfo(UeType.IMSI, "123456787654321"));
        sub.setNodeInfoList(Collections.singletonList(new NodeInfo(NodeGrouping.MME, TraceDepth.MAXIMUM, Collections.singletonList("not sure"))));
    }

    public void populateGpehSubscriptionDTO(final GpehSubscription sub) {
        populateEventSubscriptionDTO(sub);
        sub.setCellsSupported(true);
        sub.setUeFraction(1);
        sub.setApplyOnAllCells(false);
        sub.setCells(Collections.singletonList(new CellInfo("abc", "abc")));
    }

    public void populateGpehSubscriptionDTOWithManyEventsAndCells(final GpehSubscription sub) {
        populateEventSubscriptionDTOWithManyEvents(sub);
        sub.setCellsSupported(true);
        sub.setUeFraction(1);
        sub.setApplyOnAllCells(false);
        final List<CellInfo> cellInfos = new ArrayList<>();
        for (int i = 0; i < 8000; i++) {
            cellInfos.add(new CellInfo("Some long Node name" + i, "Some long UltranCellID" + i % 10));
        }
        sub.setCells(cellInfos);
    }

    public void populateCellTrafficSubscriptionDTO(final CellTrafficSubscription sub) {
        populateEventSubscriptionDTO(sub);
        sub.setCellInfoList(Collections.singletonList(new CellInfo("abc", "abc")));
        sub.setTriggerEventInfo(new TriggerEventInfo("abc", "abc"));
    }

    public void populateCellTrafficSubscriptionDTOWithManyEvents(final CellTrafficSubscription sub) {
        populateEventSubscriptionDTOWithManyEvents(sub);
        sub.setCellInfoList(Collections.singletonList(new CellInfo("abc", "abc")));
        sub.setTriggerEventInfo(new TriggerEventInfo("abc", "abc"));
    }

    public void populateCtumSubscriptionDTO(final CtumSubscription sub) {
        populateSubscriptionDTO(sub);
        sub.setOutputMode(OutputModeType.FILE);
        sub.setStreamInfo(new StreamInfo("192.168.0.42", 4554, 2));
    }

    public void populateUetrSubscriptionDTO(final UetrSubscription sub) {
        populateEventSubscriptionDTO(sub);
        sub.setUeInfo(Collections.singletonList(new UeInfo(UeType.IMEI, "123456789")));
    }

    public void populateUetrSubscriptionDTOWithManyEvents(final UetrSubscription sub) {
        populateEventSubscriptionDTOWithManyEvents(sub);
        sub.setUeInfo(Collections.singletonList(new UeInfo(UeType.IMEI, "123456789")));
    }

    public Entity toEntity(final String poType, final Long poId, final String poFdn, final Map<String, Object> attributes,
                           final Map<String, Collection<PersistenceObject>> associations) {
        final Entity entity = new Entity(poType, poId, poFdn);

        // attributes
        entity.getAttributes().putAll(attributes);

        if (associations.isEmpty()) {
            return entity;
        }

        // associations
        for (final Map.Entry<String, Collection<PersistenceObject>> association : associations.entrySet()) {
            final String associationName = association.getKey();
            final Collection<PersistenceObject> persistenceObjects = association.getValue();
            final List<Long> associationIds;
            final List<Entity> associationEntities;
            if (persistenceObjects.isEmpty()) {
                associationIds = Collections.emptyList();
                associationEntities = Collections.emptyList();
            } else {
                associationIds = new ArrayList<>(persistenceObjects.size());
                associationEntities = new ArrayList<>(persistenceObjects.size());
                for (final PersistenceObject persistenceObject : persistenceObjects) {
                    associationIds.add(persistenceObject.getPoId());
                    // check if persistenceObject is an ManagedObject to retrieve FDN, we don't care about its associations
                    String fdn = null;
                    if (persistenceObject instanceof ManagedObject) {
                        fdn = ((ManagedObject) persistenceObject).getFdn();
                    }
                    associationEntities.add(new Entity(persistenceObject.getType(), persistenceObject.getPoId(), fdn));
                }
            }
            entity.getAssociationsIds().put(associationName, associationIds);
            entity.getAssociations().put(associationName, associationEntities);
        }
        return entity;
    }

}
