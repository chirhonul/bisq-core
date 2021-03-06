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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.governance.ballot.Ballot;
import bisq.core.dao.governance.ballot.BallotList;
import bisq.core.dao.governance.ballot.vote.Vote;
import bisq.core.dao.governance.merit.MeritList;
import bisq.core.dao.state.BsqStateService;

import java.util.Optional;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds all data from a decrypted vote item.
 */
//TODO rename  DecryptedBallotsWithMerits
@Slf4j
@Value
public class DecryptedVote {
    private final byte[] hashOfBlindVoteList;
    private final String voteRevealTxId; // not used yet but keep it for now
    private final String blindVoteTxId; // not used yet but keep it for now
    private final long stake;
    private final BallotList ballotList;
    private final MeritList meritList;

    DecryptedVote(byte[] hashOfBlindVoteList, String voteRevealTxId, String blindVoteTxId, long stake,
                  BallotList ballotList, MeritList meritList) {
        this.hashOfBlindVoteList = hashOfBlindVoteList;
        this.voteRevealTxId = voteRevealTxId;
        this.blindVoteTxId = blindVoteTxId;
        this.stake = stake;
        this.ballotList = ballotList;
        this.meritList = meritList;
    }

    public Optional<Vote> getVote(String proposalTxId) {
        return ballotList.stream()
                .filter(ballot -> ballot.getTxId().equals(proposalTxId))
                .map(Ballot::getVote)
                .findAny();
    }

    public long getMerit(BsqStateService bsqStateService) {
        return VoteResultConsensus.getMeritStake(blindVoteTxId, meritList, bsqStateService);
    }
}
