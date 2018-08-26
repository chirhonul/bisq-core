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

package bisq.core.dao.node.parser;

import bisq.core.dao.bonding.lockup.LockupType;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import bisq.common.app.DevEnv;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Processes OpReturn output if valid and delegates validation to specific validators.
 */
@Slf4j
public class OpReturnParser {
    private final OpReturnProposalParser opReturnProposalParser;
    private final OpReturnCompReqParser opReturnCompReqParser;
    private final OpReturnBlindVoteParser opReturnBlindVoteParser;
    private final OpReturnVoteRevealParser opReturnVoteRevealParser;
    private final OpReturnLockupParser opReturnLockupParser;

    @Inject
    public OpReturnParser(OpReturnProposalParser opReturnProposalParser,
                          OpReturnCompReqParser opReturnCompReqParser,
                          OpReturnBlindVoteParser opReturnBlindVoteParser,
                          OpReturnVoteRevealParser opReturnVoteRevealParser,
                          OpReturnLockupParser opReturnLockupParser) {

        this.opReturnProposalParser = opReturnProposalParser;
        this.opReturnCompReqParser = opReturnCompReqParser;
        this.opReturnBlindVoteParser = opReturnBlindVoteParser;
        this.opReturnVoteRevealParser = opReturnVoteRevealParser;
        this.opReturnLockupParser = opReturnLockupParser;
    }

    // We only check partially the rules here as we do not know the BSQ fee at that moment which is always used when
    // we have OP_RETURN data.
    public void processOpReturnCandidate(TempTxOutput txOutput, ParsingModel parsingModel) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        if (txOutput.getValue() == 0 && opReturnData != null && opReturnData.length >= 1) {
            OpReturnType.getOpReturnType(opReturnData[0])
                    .ifPresent(parsingModel::setOpReturnTypeCandidate);
        }
    }

    /**
     * Parse the type of OP_RETURN data and validate it.
     *
     * @param txOutput      The temporary transaction output to parse.
     * @param lastOutput    If true, the output being parsed has a non-zero value.
     * @param blockheight   The height of the block containing the tx.
     * @param bsqFee        The fee which should be paid in BSQ.
     * @param parsingModel  The parsing model.
     * @return              The type of the transaction output, which will be either one of the
     *                          {@code *_OP_RETURN_OUTPUT} values, or {@code UNDEFINED} in case of
     *                          unexpected state.
     *
     * todo(chirhonul): simplify signature by combining types: txOutput, lastOutput, bsqFee, blockHeight all seem related
     */
    public TxOutputType parseAndValidate(TempTxOutput txOutput, boolean lastOutput, int blockheight, long bsqFee, ParsingModel parsingModel) {
        boolean nonZeroOutput = txOutput.getValue() != 0;
        byte[] opReturnData = txOutput.getOpReturnData();
        if (nonZeroOutput || !lastOutput || opReturnData.length < 1) {
            log.warn("OP_RETURN data does not match our rules. opReturnData={}", Utils.HEX.encode(opReturnData));
            return TxOutputType.UNDEFINED;
        }
        final Optional<OpReturnType> optionalOpReturnType = OpReturnType.getOpReturnType(opReturnData[0]);
        if (!optionalOpReturnType.isPresent()) {
            // TODO(chirhonul): should raise exception? then caller can log info about the raw tx.
            log.warn("OP_RETURN data does not match our defined types. opReturnData={}", Utils.HEX.encode(opReturnData));
            return TxOutputType.UNDEFINED;
        }
        TxOutputType outputType = validate(opReturnData, blockheight, bsqFee, parsingModel, optionalOpReturnType.get());
        return outputType;
    }

    /**
     * Validate that the OP_RETURN data is correct for specified type.
     *
     * @param opReturnData The raw OP_RETURN data.
     * @param blockheight  The height of the block of the transaction with the OP_RETURN
     * @param bsqFee       The fee in BSQ for the operation.
     * @param parsingModel The parsing model.
     * @param opReturnType The type of the OP_RETURN operation.
     * @return             The type of transaction output, which will be either one of the
     *                          {@code *_OP_RETURN_OUTPUT} values, or {@code UNDEFINED} in case of
     *                          unexpected state.
     */
    private TxOutputType validate(byte[] opReturnData, int blockheight, long bsqFee,
                                 ParsingModel parsingModel, OpReturnType opReturnType) {
        TxOutputType outputType = TxOutputType.UNDEFINED;
        switch (opReturnType) {
            case PROPOSAL:
                outputType = processProposal(opReturnData, bsqFee, blockheight, parsingModel);
                break;
            case COMPENSATION_REQUEST:
                outputType = processCompensationRequest(opReturnData, bsqFee, blockheight, parsingModel);
                break;
            case BLIND_VOTE:
                outputType = processBlindVote(opReturnData, bsqFee, blockheight, parsingModel);
                break;
            case VOTE_REVEAL:
                outputType = processVoteReveal(opReturnData, blockheight, parsingModel);
                break;
            case LOCKUP:
                outputType = processLockup(opReturnData, blockheight, parsingModel);
                break;
            default:
                // Should never happen as long we keep OpReturnType entries in sync with out switch case.
                // todo(chirhonul): should raise? then we can go back to logging what tx consists of
                // where we catch the exception.
                final String msg = "Unsupported OpReturnType. tx at height=" + blockheight +
                        "; opReturnData=" + Utils.HEX.encode(opReturnData);
                log.error(msg);
                DevEnv.logErrorAndThrowIfDevMode(msg);
                break;
        }
        return outputType;
    }

    /**
     *
     * @param opReturnData
     * @param bsqFee
     * @param blockHeight
     * @param parsingModel
     * @return
     *
     * todo(chirhonul): drop parsingModel entirely, by either returning the OpReturnType.PROPOSAL or
     * inferring that this is the verified OP_RETURN type in the caller.
     */
    private TxOutputType processProposal(byte[] opReturnData, long bsqFee, int blockHeight, ParsingModel parsingModel) {
        if (opReturnProposalParser.validate(opReturnData, bsqFee, blockHeight, null)) {
            parsingModel.setVerifiedOpReturnType(OpReturnType.PROPOSAL);
            return TxOutputType.PROPOSAL_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a proposal op_return data but it did not " +
                    "match our rules. blockHeight={}", blockHeight);
            return TxOutputType.INVALID_OUTPUT;
        }
    }

    private TxOutputType processCompensationRequest(byte[] opReturnData, long bsqFee, int blockHeight, ParsingModel parsingModel) {
        final TempTxOutput issuanceCandidate = parsingModel.getIssuanceCandidate();
        if (opReturnCompReqParser.validate(opReturnData, bsqFee, blockHeight, parsingModel)) {
            if (issuanceCandidate != null)
                issuanceCandidate.setTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
            parsingModel.setVerifiedOpReturnType(OpReturnType.COMPENSATION_REQUEST);
            return TxOutputType.COMP_REQ_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a compensation request op_return data but it did not " +
                    "match our rules. blockHeight={}", blockHeight);

            // If the opReturn is invalid the issuance candidate cannot become BSQ, so we set it to BTC
            if (issuanceCandidate != null)
                issuanceCandidate.setTxOutputType(TxOutputType.BTC_OUTPUT);
            return TxOutputType.INVALID_OUTPUT;
        }
    }

    private TxOutputType processBlindVote(byte[] opReturnData, long bsqFee, int blockHeight, ParsingModel parsingModel) {
        if (opReturnBlindVoteParser.validate(opReturnData, bsqFee, blockHeight, parsingModel)) {
            parsingModel.setVerifiedOpReturnType(OpReturnType.BLIND_VOTE);
            return TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a blind vote op_return data but it did not " +
                    "match our rules. blockHeight={}", blockHeight);

            TempTxOutput blindVoteLockStakeOutput = parsingModel.getBlindVoteLockStakeOutput();
            if (blindVoteLockStakeOutput != null)
                blindVoteLockStakeOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
            return TxOutputType.INVALID_OUTPUT;
        }
    }

    private TxOutputType processVoteReveal(byte[] opReturnData, int blockHeight, ParsingModel parsingModel) {
        if (opReturnVoteRevealParser.validate(opReturnData, blockHeight, parsingModel)) {
            parsingModel.setVerifiedOpReturnType(OpReturnType.VOTE_REVEAL);
            return TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a vote reveal op_return data but it did not " +
                    "match our rules. blockHeight={}", blockHeight);

            TempTxOutput voteRevealUnlockStakeOutput = parsingModel.getVoteRevealUnlockStakeOutput();
            if (voteRevealUnlockStakeOutput != null)
                voteRevealUnlockStakeOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
            return TxOutputType.INVALID_OUTPUT;
        }
    }

    private TxOutputType processLockup(byte[] opReturnData, int blockHeight, ParsingModel parsingModel) {
        Optional<LockupType> lockupType = LockupType.getLockupType(opReturnData[2]);
        int lockTime = Utilities.byteArrayToInteger(Arrays.copyOfRange(opReturnData, 3, 5));

        if (opReturnLockupParser.validate(opReturnData, lockupType, lockTime, blockHeight, parsingModel)) {
            parsingModel.setVerifiedOpReturnType(OpReturnType.LOCKUP);
            parsingModel.getTx().setLockTime(lockTime);
            return TxOutputType.LOCKUP_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a lockup op_return data but it did not " +
                    "match our rules. blockHeight={}", blockHeight);

            // If the opReturn is invalid the lockup candidate cannot become BSQ, so we set it to BTC
            TempTxOutput lockupCandidate = parsingModel.getLockupOutput();
            if (lockupCandidate != null)
                lockupCandidate.setTxOutputType(TxOutputType.BTC_OUTPUT);
            return TxOutputType.INVALID_OUTPUT;
        }
    }
}
