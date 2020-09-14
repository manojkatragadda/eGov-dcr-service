/*
 *    eGov  SmartCity eGovernance suite aims to improve the internal efficiency,transparency,
 *    accountability and the service delivery of the government  organizations.
 *
 *     Copyright (C) 2017  eGovernments Foundation
 *
 *     The updated version of eGov suite of products as by eGovernments Foundation
 *     is available at http://www.egovernments.org
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see http://www.gnu.org/licenses/ or
 *     http://www.gnu.org/licenses/gpl.html .
 *
 *     In addition to the terms of the GPL license to be adhered to in using this
 *     program, the following additional terms are to be complied with:
 *
 *         1) All versions of this program, verbatim or modified must carry this
 *            Legal Notice.
 *            Further, all user interfaces, including but not limited to citizen facing interfaces,
 *            Urban Local Bodies interfaces, dashboards, mobile applications, of the program and any
 *            derived works should carry eGovernments Foundation logo on the top right corner.
 *
 *            For the logo, please refer http://egovernments.org/html/logo/egov_logo.png.
 *            For any further queries on attribution, including queries on brand guidelines,
 *            please contact contact@egovernments.org
 *
 *         2) Any misrepresentation of the origin of the material is prohibited. It
 *            is required that all modified versions of this material be marked in
 *            reasonable ways as different from the original version.
 *
 *         3) This license does not grant any rights to any user of the program
 *            with regards to rights under trademark law for use of the trade names
 *            or trademarks of eGovernments Foundation.
 *
 *   In case of any queries, you can reach eGovernments Foundation at contact@egovernments.org.
 *
 */

package org.egov.infra.workflow.entity;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.egov.infra.admin.master.entity.User;
import org.egov.infra.exception.ApplicationRuntimeException;
import org.egov.infra.persistence.entity.AbstractAuditable;
import org.egov.infra.utils.JsonUtils;
import org.egov.infra.workflow.entity.State.StateStatus;
import org.egov.infra.workflow.entity.contract.StateInfoBuilder;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.joda.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

@MappedSuperclass
public abstract class StateAware<T extends OwnerGroup> extends AbstractAuditable {
    private static final long serialVersionUID = 5776408218810221246L;

    @ManyToOne(targetEntity = State.class, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "STATE_ID")
    private State<T> state;

    @Transient
    @JsonIgnore
    private Transition transition;

    /**
     * Need to overridden by the implementing class to give details about the State <I>Used by Inbox to fetch the State Detail at
     * runtime</I>
     *
     * @return String Detail
     */
    public abstract String getStateDetails();

    /**
     * To set the Group Link, Any State Aware Object which needs Grouping should override this method
     **/
    public String myLinkId() {
        return getId().toString();
    }

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    public State<T> getState() {
        return state;
    }

    private void setState(State state) {
        this.state = state;
    }

    public final State<T> getCurrentState() {
        return getState();
    }

    public final T currentAssignee() {
        return getState().getOwnerPosition();
    }

    public final T previousAssignee() {
        return getState().getPreviousOwner();
    }

    public final List<StateHistory<T>> getStateHistory() {
        return state == null ? Collections.emptyList() : new LinkedList(getState().getHistory());
    }

    public final String getStateType() {
        return this.getClass().getSimpleName();
    }

    public <S> S extraInfoAs(Class<S> type) {
        return state.extraInfoAs(type);
    }

    public final boolean transitionInitialized() {
        return hasState() && getCurrentState().isNew();
    }

    public final boolean transitionCompleted() {
        return hasState() && getCurrentState().isEnded();
    }

    public final boolean transitionInprogress() {
        return hasState() && (transitionInitialized() || getCurrentState().isInprogress());
    }

    public final boolean hasState() {
        return getCurrentState() != null;
    }

    public final Transition transition() {
        if (this.transition == null)
            this.transition = new Transition();
        return this.transition;
    }

    public final void changeProcessOwner(T position) {
        if (transitionInprogress())
            this.state.setOwnerPosition(position);
    }

    public final void changeProcessInitiator(T position) {
        if (transitionInprogress())
            this.state.setInitiatorPosition(position);
    }

    protected StateInfoBuilder buildStateInfo() {
        return new StateInfoBuilder().task(this.getState().getNatureOfTask()).itemDetails(this.getStateDetails())
                .status(getCurrentState().getStatus().name()).refDate(getCreatedDate()).sender(this.getState().getSenderName())
                .senderPhoneno(this.getState().getExtraInfo());
    }

    public String getStateInfoJson() {
        return this.buildStateInfo().toJson();
    }

    public final class Transition implements Serializable {
        private static final long serialVersionUID = -6035435855091367838L;
        private boolean transitionInitiated;

        private void checkinTransition() {
            if (transitionInitiated)
                throw new ApplicationRuntimeException("Calling multiple transitions not supported");
            transitionInitiated = true;
        }

        private void checkTransitionStatus() {
            if (!transitionInitiated)
                throw new ApplicationRuntimeException("Use start|progress|end API before setting values");
        }

        public final Transition start() {
            checkinTransition();
            if (hasState())
                throw new ApplicationRuntimeException("Transition already started.");
            else {
                setState(new State());
                state.setType(getStateType());
                state.setStatus(StateStatus.STARTED);
                state.setValue(State.DEFAULT_STATE_VALUE_CREATED);
                state.setComments(State.DEFAULT_STATE_VALUE_CREATED);
            }
            return this;
        }

        public final Transition startNext() {
            if (state == null)
                throw new ApplicationRuntimeException("Transition never started, use start() instead");
            if (!transitionCompleted())
                throw new ApplicationRuntimeException("Transition can not be started, end the current transition first");
            State previousState = state;
            state = null;
            start();
            state.setPreviousStateRef(previousState);
            return this;
        }

        public final Transition progress() {
            T previousOwner = state.getOwnerPosition();
            progressWithStateCopy();
            resetState();
            state.setPreviousOwner(previousOwner);
            return this;
        }

        public final Transition progressWithStateCopy() {
            checkinTransition();
            if (transitionCompleted())
                throw new ApplicationRuntimeException("Transition already ended");
            if (hasState()) {
                state.addStateHistory(new StateHistory(state));
                state.setPreviousOwner(state.getOwnerPosition());
                state.setStatus(StateStatus.INPROGRESS);
            }
            return this;
        }

        public final Transition end() {
            checkinTransition();
            if (transitionCompleted())
                throw new ApplicationRuntimeException("Transition already ended");
            else {
                state.addStateHistory(new StateHistory(state));
                state.setValue(State.DEFAULT_STATE_VALUE_CLOSED);
                state.setStatus(StateStatus.ENDED);
                state.setComments(State.DEFAULT_STATE_VALUE_CLOSED);
            }
            return this;
        }

        public final Transition reopen() {
            checkinTransition();
            if (transitionCompleted()) {
                state.addStateHistory(new StateHistory(state));
                state.setPreviousOwner(state.getOwnerPosition());
                state.setValue(State.STATE_REOPENED);
                state.setStatus(StateStatus.INPROGRESS);
            } else
                throw new ApplicationRuntimeException("Transition can not be reopened, end the current transition first");
            return this;
        }

        public final Transition withOwner(User owner) {
            checkTransitionStatus();
            state.setOwnerUser(owner);
            return this;
        }

        public final Transition withOwner(T owner) {
            checkTransitionStatus();
            state.setOwnerPosition(owner);
            return this;
        }

        public final Transition withInitiator(T owner) {
            checkTransitionStatus();
            state.setInitiatorPosition(owner);
            return this;
        }

        public final Transition withStateValue(String currentStateValue) {
            checkTransitionStatus();
            state.setValue(currentStateValue);
            return this;
        }

        public final Transition withNextAction(String nextAction) {
            checkTransitionStatus();
            state.setNextAction(nextAction);
            return this;
        }

        public final Transition withComments(String comments) {
            checkTransitionStatus();
            state.setComments(comments);
            return this;
        }

        public final Transition withNatureOfTask(String natureOfTask) {
            checkTransitionStatus();
            state.setNatureOfTask(natureOfTask);
            return this;
        }

        public final Transition withExtraInfo(String extraInfo) {
            checkTransitionStatus();
            state.setExtraInfo(extraInfo);
            return this;
        }

        public final Transition withExtraInfo(Object extraInfo) {
            checkTransitionStatus();
            state.setExtraInfo(JsonUtils.toJSON(extraInfo));
            return this;
        }

        public final Transition withDateInfo(Date dateInfo) {
            checkTransitionStatus();
            state.setDateInfo(dateInfo);
            return this;
        }

        public final Transition withExtraDateInfo(Date extraDateInfo) {
            checkTransitionStatus();
            state.setExtraDateInfo(extraDateInfo);
            return this;
        }

        public final Transition withSenderName(String senderName) {
            checkTransitionStatus();
            state.setSenderName(senderName);
            return this;
        }

        public final Transition withSLA(Date slaDate) {
            checkTransitionStatus();
            state.setSla(slaDate);
            return this;
        }
        
        public final Transition withSLA(int slaDays) {
            return withSLA(new LocalDateTime().plusDays(slaDays).toDate());
        }

        public final Transition withSLAHours(int slaHours) {
            return withSLA(new LocalDateTime().plusHours(slaHours).toDate());
        }

        private void resetState() {
            state.setComments(EMPTY);
            state.setDateInfo(null);
            state.setExtraDateInfo(null);
            state.setExtraInfo(EMPTY);
            state.setNextAction(EMPTY);
            state.setValue(EMPTY);
            state.setSenderName(EMPTY);
            state.setNatureOfTask(EMPTY);
            state.setOwnerUser(null);
            state.setOwnerPosition(null);
            state.setInitiatorPosition(null);
            state.setSla(null);
        }

    }
}