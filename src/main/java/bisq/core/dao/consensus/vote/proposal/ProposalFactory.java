/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.consensus.vote.proposal;

import bisq.core.dao.consensus.state.events.payloads.Proposal;
import bisq.core.dao.consensus.vote.proposal.compensation.CompensationRequestBallot;
import bisq.core.dao.consensus.vote.proposal.generic.GenericBallot;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProposalFactory {
    public static Ballot getProposalFromPayload(Proposal proposal) {
        switch (proposal.getType()) {
            case COMPENSATION_REQUEST:
                return new CompensationRequestBallot(proposal);
            case GENERIC:
                return new GenericBallot(proposal);
            case CHANGE_PARAM:
                //TODO
                throw new RuntimeException("Not implemented yet");
            case REMOVE_ALTCOIN:
                //TODO
                throw new RuntimeException("Not implemented yet");
            default:
                final String msg = "Undefined ProposalType " + proposal.getType();
                log.error(msg);
                throw new RuntimeException(msg);
        }
    }
}
