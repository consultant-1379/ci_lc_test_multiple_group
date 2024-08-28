/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.subscription.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.CriteriaSpecification;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.NodeGrouping;
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.TraceDepth;
import com.ericsson.oss.pmic.dto.subscription.enums.UeType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;

public class SubscriptionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final static String NETWORK_ELEMENT = "NetworkElement=";

    private String name;
    private String description;
    private String owner;
    private int ropInfo;
    private String userType;
    private String jobStatus;
    private String subsType;
    private String opState;
    private String adminState;
    private boolean isPnP;
    private boolean filterOnME;
    private boolean filterOnMF;
    private String nodes;
    private String moAndCounters;
    private int startTimeOffset;
    private int endTimeOffset;
    private String expectedResult;
    private String outputMode;
    private int ueFraction;
    private boolean isAsnEnbled;
    private String ipAddress;
    private int port;
    private boolean cbs;
    private List<CriteriaSpecification> criteriaSpecification;
    private int nodeListIdentity;
    private String pibParam;
    private String ueInfoType;
    private int ueInfoValue;
    private String nodeInfoGrouping;
    private String traceDepth;
    private String interfaceType;
    private List<UeInfo> ueInfoList;
    private int portOffset;

    public SubscriptionInfo addName(final String name) {
        this.name = name;
        return this;
    }

    public SubscriptionInfo addDescription(final String description) {
        this.description = description;
        return this;
    }

    public SubscriptionInfo addOwner(final String owner) {
        this.owner = owner;
        return this;
    }

    public SubscriptionInfo addRopInfo(final int ropInfo) {
        this.ropInfo = ropInfo;
        return this;
    }

    public SubscriptionInfo addUserType(final String userType) {
        this.userType = userType;
        return this;
    }

    public SubscriptionInfo addJobStatus(final String jobStatus) {
        this.jobStatus = jobStatus;
        return this;
    }

    public SubscriptionInfo addSubsType(final String subsType) {
        this.subsType = subsType;
        return this;
    }

    public SubscriptionInfo addOpState(final String opState) {
        this.opState = opState;
        return this;
    }

    public SubscriptionInfo addAdminState(final String adminState) {
        this.adminState = adminState;
        return this;
    }

    public SubscriptionInfo addIsPnP(final boolean isPnP) {
        this.isPnP = isPnP;
        return this;
    }

    public SubscriptionInfo addFilterOnME(final boolean filterOnME) {
        this.filterOnME = filterOnME;
        return this;
    }

    public SubscriptionInfo addFilterOnMF(final boolean filterOnMF) {
        this.filterOnMF = filterOnMF;
        return this;
    }

    public SubscriptionInfo addNodes(final String nodes) {
        this.nodes = nodes;
        return this;
    }

    public SubscriptionInfo addMoAndCounter(final String moCounters) {
        moAndCounters = moCounters;
        return this;
    }

    public SubscriptionInfo addStartTimeOffset(final int startTimeOffset) {
        this.startTimeOffset = startTimeOffset;
        return this;
    }

    public SubscriptionInfo addEndTimeOffset(final int endTimeOffset) {
        this.endTimeOffset = endTimeOffset;
        return this;
    }

    public SubscriptionInfo addExpectedResult(final String expectedResult) {
        this.expectedResult = expectedResult;
        return this;
    }

    public SubscriptionInfo addOutputMode(final String outputMode) {
        this.outputMode = outputMode;
        return this;
    }

    public SubscriptionInfo addUeFraction(final int ueFraction) {
        this.ueFraction = ueFraction;
        return this;
    }

    public SubscriptionInfo addIsAsnEnabled(final boolean isAsnEnabled) {
        isAsnEnbled = isAsnEnabled;
        return this;
    }

    public SubscriptionInfo addIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public SubscriptionInfo addPort(final int port) {
        this.port = port;
        return this;
    }

    public SubscriptionInfo addUeInfoType(final String ueInfoType) {
        this.ueInfoType = ueInfoType;
        return this;
    }

    public SubscriptionInfo addUeInfoValue(final int ueInfoValue) {
        this.ueInfoValue = ueInfoValue;
        return this;
    }

    public SubscriptionInfo addNodeInfoGrouping(final String nodeInfoGrouping) {
        this.nodeInfoGrouping = nodeInfoGrouping;
        return this;
    }

    public SubscriptionInfo addTraceDepth(final String traceDepth) {
        this.traceDepth = traceDepth;
        return this;
    }

    public SubscriptionInfo addInterfaceType(final String interfaceType) {
        this.interfaceType = interfaceType;
        return this;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public String getDescription() {
        return description;

    }

    public RopPeriod getRopInfo() {
        return RopPeriod.fromSeconds(ropInfo);
    }

    public UserType getUserType() {
        if (UserType.USER_DEF.name().equals(userType)) {
            return UserType.USER_DEF;
        }
        return UserType.SYSTEM_DEF;

    }

    public TaskStatus getJobStatus() {
        if (TaskStatus.OK.name().equals(jobStatus)) {
            return TaskStatus.OK;
        } else if (TaskStatus.ERROR.name().equals(jobStatus)) {
            return TaskStatus.ERROR;
        }
        return TaskStatus.NA;
    }

    public SubscriptionType getSubscriptionType() {
        if (SubscriptionType.CELLTRACE.name().equals(subsType)) {
            return SubscriptionType.CELLTRACE;
        } else if (SubscriptionType.UETRACE.name().equals(subsType)) {
            return SubscriptionType.UETRACE;
        } else if (SubscriptionType.UETR.name().equals(subsType)) {
            return SubscriptionType.UETR;
        }

        return SubscriptionType.STATISTICAL;
    }

    public OperationalState getOpState() {
        if (OperationalState.RUNNING.name().equals(opState)) {
            return OperationalState.RUNNING;
        } else if (OperationalState.SCHEDULED.name().equals(opState)) {
            return OperationalState.SCHEDULED;
        }

        return OperationalState.NA;
    }

    public AdministrationState getAdminState() {
        return AdministrationState.valueOf(adminState);
    }

    public Boolean isPnP() {
        return isPnP;
    }

    public Boolean isFileterOnME() {
        return filterOnME;
    }

    public Boolean isFileterOnMF() {
        return filterOnMF;
    }

    public List<String> getNodes() {
        final List<String> nodesList = new ArrayList<>();
        final String[] nodeNames = nodes.split(",");
        for (final String nodeName : nodeNames) {
            nodesList.add(NETWORK_ELEMENT + nodeName);
        }

        return nodesList;
    }

    public List<CounterInfo> getCounters() {
        final List<CounterInfo> ountersInfoList = new ArrayList<>();
        if (moAndCounters != null) {
            final String[] counterInfos = moAndCounters.split(",");

            for (final String counterData : counterInfos) {
                final String[] moCounter = counterData.split(":");
                final String moName = moCounter[0];
                final String[] counters = moCounter[1].split("-");
                for (final String counter : counters) {
                    final CounterInfo counterInfo = new CounterInfo(counter, moName);
                    ountersInfoList.add(counterInfo);
                }
            }
        }

        return ountersInfoList;
    }

    public List<EventInfo> getEvents() {
        final List<EventInfo> eventInfoList = new ArrayList<EventInfo>();
        if (moAndCounters != null) {
            final String[] groupsEvents = moAndCounters.split(",");

            for (final String groupNameEvent : groupsEvents) {
                final String[] groupSpliter = groupNameEvent.split(":");
                final String groupName = groupSpliter[0];
                final String[] events = groupSpliter[1].split("-");
                for (final String event : events) {
                    final EventInfo eventInfo = new EventInfo(event, groupName);
                    eventInfoList.add(eventInfo);
                }
            }
        }

        return eventInfoList;

    }

    public int getStartTimeOffset() {
        return startTimeOffset;

    }

    public int getEndTimeOffset() {
        return endTimeOffset;
    }

    public Date getStartTime() {
        Date startTime = null;
        if (startTimeOffset > 0) {
            final Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, startTimeOffset);
            startTime = cal.getTime();
        }
        return startTime;
    }

    public Date getEndTime() {
        Date endTime = null;
        if (endTimeOffset > 0) {
            final Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, endTimeOffset);
            endTime = cal.getTime();
        }
        return endTime;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    /**
     * @return the outputMode
     */
    public OutputModeType getOutputMode() {
        if (OutputModeType.FILE_AND_STREAMING.name().equals(outputMode)) {
            return OutputModeType.FILE_AND_STREAMING;
        } else if (OutputModeType.FILE.name().equals(outputMode)) {
            return OutputModeType.FILE;
        }

        return OutputModeType.STREAMING;
    }

    /**
     * @param outputMode
     *         the outputMode to set
     */
    public void setOutputMode(final String outputMode) {
        this.outputMode = outputMode;
    }

    /**
     * @return the ueFraction
     */
    public int getUeFraction() {
        return ueFraction;
    }

    /**
     * @param ueFraction
     *         the ueFraction to set
     */
    public void setUeFraction(final int ueFraction) {
        this.ueFraction = ueFraction;
    }

    /**
     * @return the isAsnEnbled
     */
    public boolean isAsnEnbled() {
        return isAsnEnbled;
    }

    /**
     * @param isAsnEnbled
     *         the isAsnEnbled to set
     */
    public void setAsnEnbled(final boolean isAsnEnbled) {
        this.isAsnEnbled = isAsnEnbled;
    }

    /**
     * @return the ipAddress
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * @param ipAddress
     *         the ipAddress to set
     */
    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port
     *         the port to set
     */
    public void setPort(final int port) {
        this.port = port;
    }

    public boolean getCbs() {
        return cbs;
    }

    public SubscriptionInfo addCbs(final boolean cbs) {
        this.cbs = cbs;
        return this;
    }

    public List<CriteriaSpecification> getCriteriaSpecification() {
        return criteriaSpecification;
    }

    public SubscriptionInfo addCriteriaSpecification(final String idString) {
        final CriteriaSpecification csFromString = new CriteriaSpecification();
        csFromString.setName("TestCriteria");
        csFromString.setCriteriaIdString(idString);

        if (criteriaSpecification == null) {
            criteriaSpecification = new ArrayList<CriteriaSpecification>();
        }
        criteriaSpecification.add(csFromString);
        return this;
    }

    public SubscriptionInfo addCriteriaSpecification(final List<CriteriaSpecification> criteriaSpecification) {
        this.criteriaSpecification = criteriaSpecification;
        return this;
    }

    public int getNodeListIdentity() {
        return nodeListIdentity;
    }

    public SubscriptionInfo addNodeListIdentity(final int nodeListIdentity) {
        this.nodeListIdentity = nodeListIdentity;
        return this;
    }

    public String getPibParam() {
        return pibParam;
    }

    public SubscriptionInfo addPibParam(final String pibParam) {
        this.pibParam = pibParam;
        return this;
    }

    public SubscriptionInfo addPortOffset(final int portOffset) {
        this.portOffset = portOffset;
        return this;
    }

    public SubscriptionInfo addUeInfoList(final String imsis) {
        final List<String> imsiList = new ArrayList<>(Arrays.asList(imsis.split(";")));
        List<UeInfo> ueInfos = new ArrayList<>(imsiList.size());
        for (String imsi : imsiList) {
            UeInfo ueInfo = new UeInfo();
            ueInfo.setType(UeType.IMSI);
            ueInfo.setValue(imsi);
            ueInfos.add(ueInfo);
        }
        this.ueInfoList = ueInfos;
        return this;
    }

    public Map<String, Object> getSubscriptionInfoAsMap() {
        final Map<String, Object> allAttribue = new HashMap<String, Object>();
        allAttribue.put("name", name);
        allAttribue.put("description", description);
        allAttribue.put("owner", owner);
        allAttribue.put("ropInfo", ropInfo);
        allAttribue.put("userType", userType);
        allAttribue.put("taskStatus", jobStatus);
        allAttribue.put("subsType", subsType);
        allAttribue.put("opState", opState);
        allAttribue.put("adminState", adminState);
        allAttribue.put("isPnP", isPnP);
        allAttribue.put("filterOnME", filterOnME);
        allAttribue.put("filterOnMF", filterOnMF);
        allAttribue.put("nodes", nodes);
        allAttribue.put("moAndCounters", moAndCounters);
        allAttribue.put("outputMode", outputMode);
        allAttribue.put("ueFraction", ueFraction);
        allAttribue.put("isAsnEnbled", isAsnEnbled);
        allAttribue.put("ipAddress", ipAddress);
        allAttribue.put("port", port);
        allAttribue.put("isCbs", cbs);
        allAttribue.put("criteriaSpecification", criteriaSpecification);
        allAttribue.put("nodeListIdentity", nodeListIdentity);
        allAttribue.put("pibParam", pibParam);
        allAttribue.put("portOffset", portOffset);
        allAttribue.put("ueInfoList", ueInfoList);
        return allAttribue;
    }

    public UeType getUeInfoType() {
        if (UeType.IMEI.name().equals(ueInfoType)) {
            return UeType.IMEI_SOFTWARE_VERSION;
        } else if (UeType.IMSI.name().equals(ueInfoType)) {
            return UeType.IMEI_SOFTWARE_VERSION;
        }
        return UeType.IMSI;
    }

    public void setUeInfoType(final String ueInfoType) {
        this.ueInfoType = ueInfoType;
    }

    public int getUeInfoValue() {
        return ueInfoValue;
    }

    public void setUeInfoValue(final int ueInfoValue) {
        this.ueInfoValue = ueInfoValue;
    }

    public NodeGrouping getNodeInfoType() {
        if (NodeGrouping.ENODEB.name().equals(nodeInfoGrouping)) {
            return NodeGrouping.ENODEB;
        }
        return NodeGrouping.MME;
    }

    public void setNodeInfoType(final String nodeInfoType) {
        nodeInfoGrouping = nodeInfoType;
    }

    public TraceDepth getTraceDepth() {
        if (TraceDepth.MEDIUM.name().equals(traceDepth)) {
            return TraceDepth.MEDIUM;
        } else if (TraceDepth.MAXIMUM.name().equals(traceDepth)) {
            return TraceDepth.MAXIMUM;
        } else if (TraceDepth.MINIMUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION.name().equals(traceDepth)) {
            return TraceDepth.MINIMUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION;
        } else if (TraceDepth.MEDIUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION.name().equals(traceDepth)) {
            return TraceDepth.MEDIUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION;
        } else if (TraceDepth.MAXIMUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION.name().equals(traceDepth)) {
            return TraceDepth.MAXIMUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION;
        } else if (TraceDepth.DISABLED.name().equals(traceDepth)) {
            return TraceDepth.DISABLED;
        }
        return TraceDepth.MINIMUM;
    }

    public void setTraceDepth(final String traceDepth) {
        this.traceDepth = traceDepth;
    }

    public String getInterfaceType() {
        return interfaceType;
    }

    public void setInterfaceType(final String interfaceType) {
        this.interfaceType = interfaceType;
    }

    public int getPortOffset() {
        return portOffset;
    }

    public List<UeInfo> getUeInfoList() {
        return ueInfoList;
    }

    @Override
    public String toString() {
        return "SubscriptionInfo [name=" + name + ", description=" + description + ", owner=" + owner + ", ropInfo=" + ropInfo + ", userType="
                + userType + ", jobStatus=" + jobStatus + ", subsType=" + subsType + ", opState=" + opState + ", adminState=" + adminState
                + ", isPnP=" + isPnP + ", filterOnME=" + filterOnME + ", filterOnMF=" + filterOnMF + ", nodes=" + nodes + ", moAndCounters="
                + moAndCounters + ", startTimeOffset=" + startTimeOffset + ", endTimeOffset=" + endTimeOffset + ", expectedResult=" + expectedResult
                + ", outputMode=" + outputMode + ", ueFraction=" + ueFraction + ", isAsnEnbled=" + isAsnEnbled + ", ipAddress=" + ipAddress
                + ", port=" + port + ", portOffset=" + portOffset + ", cbs=" + cbs + ", criteriaSpecification=" + criteriaSpecification + ", nodeListIdentity=" + nodeListIdentity
                + ", pibParam=" + pibParam + ", ueInfoType=" + ueInfoType + ", ueInfoValue=" + ueInfoValue + ", nodeInfoType=" + nodeInfoGrouping
                + ", traceDepth=" + traceDepth + ", interfaceType=" + interfaceType + ", ueInfoList=" + ueInfoList + "]";
    }

}
