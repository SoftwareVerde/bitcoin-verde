//package com.softwareverde.bitcoin.context.lazy;
//
//import com.softwareverde.bitcoin.bip.UpgradeSchedule;
//import com.softwareverde.bitcoin.block.validator.BlockValidator;
//import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
//import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
//import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
//import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
//import com.softwareverde.bitcoin.context.core.BlockHeaderValidatorContext;
//import com.softwareverde.bitcoin.inflater.TransactionInflaters;
//import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
//import com.softwareverde.bitcoin.transaction.TransactionDeflater;
//import com.softwareverde.bitcoin.transaction.TransactionInflater;
//import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
//import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
//import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
//import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
//import com.softwareverde.network.time.VolatileNetworkTime;
//
//public class LazyBlockValidatorContext extends BlockHeaderValidatorContext implements BlockValidator.Context {
//    protected final UnspentTransactionOutputContext _unspentTransactionOutputContext;
//    protected final TransactionValidatorFactory _transactionValidatorFactory;
//    protected final TransactionInflaters _transactionInflaters;
//
//    public LazyBlockValidatorContext(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager, final VolatileNetworkTime networkTime, final DifficultyCalculatorFactory difficultyCalculatorFactory, final UpgradeSchedule upgradeSchedule, final UnspentTransactionOutputContext unspentTransactionOutputContext, final TransactionValidatorFactory transactionValidatorFactory, final TransactionInflaters transactionInflaters) {
//        super(blockchainSegmentId, databaseManager, networkTime, difficultyCalculatorFactory, upgradeSchedule);
//
//        _transactionInflaters = transactionInflaters;
//        _unspentTransactionOutputContext = unspentTransactionOutputContext;
//        _transactionValidatorFactory = transactionValidatorFactory;
//    }
//
//    @Override
//    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
//        return _unspentTransactionOutputContext.getTransactionOutput(transactionOutputIdentifier);
//    }
//
//    @Override
//    public Long getBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier) {
//        return _unspentTransactionOutputContext.getBlockHeight(transactionOutputIdentifier);
//    }
//
////    @Override
////    public Sha256Hash getBlockHash(final TransactionOutputIdentifier transactionOutputIdentifier) {
////        return _unspentTransactionOutputContext.getBlockHash(transactionOutputIdentifier);
////    }
//
//    @Override
//    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
//        return _unspentTransactionOutputContext.isCoinbaseTransactionOutput(transactionOutputIdentifier);
//    }
//
//    @Override
//    public Boolean isPreActivationTokenForgery(final TransactionOutputIdentifier transactionOutputIdentifier) {
//        return _unspentTransactionOutputContext.isPreActivationTokenForgery(transactionOutputIdentifier);
//    }
//
//    @Override
//    public TransactionValidator getTransactionValidator(final BlockOutputs blockOutputs, final TransactionValidator.Context transactionValidatorContext) {
//        return _transactionValidatorFactory.getTransactionValidator(blockOutputs, transactionValidatorContext);
//    }
//
//    @Override
//    public TransactionInflater getTransactionInflater() {
//        return _transactionInflaters.getTransactionInflater();
//    }
//
//    @Override
//    public TransactionDeflater getTransactionDeflater() {
//        return _transactionInflaters.getTransactionDeflater();
//    }
//}