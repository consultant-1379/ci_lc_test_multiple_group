/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.pmjobs.sync;

/**
 * Value Object for PmJob
 */
public class PMICJobInfoVO {
    private String jobId;
    private String jobName;
    private String jobStatus;
    private String jobProcessType;

    /**
     * No arg Constructor
     */
    public PMICJobInfoVO() {
    }

    /**
     * Constructor
     *
     * @param jobId
     *         - Job Id
     * @param jobName
     *         - Job Name
     * @param jobStatus
     *         - Job Status
     * @param jobProcessType
     *         -Job Process Type
     */
    public PMICJobInfoVO(final String jobId, final String jobName, final String jobStatus, final String jobProcessType) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.jobStatus = jobStatus;
        this.jobProcessType = jobProcessType;
    }

    /**
     * @return the jobId
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * @param jobId
     *         the jobId to set
     */
    public void setJobId(final String jobId) {
        this.jobId = jobId;
    }

    /**
     * @return the jobName
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * @param jobName
     *         the jobName to set
     */
    public void setJobName(final String jobName) {
        this.jobName = jobName;
    }

    /**
     * @return the jobStatus
     */
    public String getJobStatus() {
        return jobStatus;
    }

    /**
     * @param jobStatus
     *         the jobStatus to set
     */
    public void setJobStatus(final String jobStatus) {
        this.jobStatus = jobStatus;
    }

    /**
     * @return the jobProcessType
     */
    public String getJobProcessType() {
        return jobProcessType;
    }

    /**
     * @param jobProcessType
     *         the jobProcessType
     */
    public void setJobProcessType(final String jobProcessType) {
        this.jobProcessType = jobProcessType;
    }
}
