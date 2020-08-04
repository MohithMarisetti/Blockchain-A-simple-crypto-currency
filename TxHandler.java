import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class TxHandler {

    UTXOPool uPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    
    public TxHandler(UTXOPool utxoPool) {
        
         this.uPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        
        //                       System.out.println("Entered isValidTx() :");
        //                       System.out.println(tx.getInput(0).prevTxHash);
        //                       System.out.println(tx.getOutputs());
        


        double totalInputOfCurrentTx = 0.0;
        double totalOutputOfCurrentTx = 0.0;

        
        //------------------------------- CONDITION-1 START ---------------------------------------
        // Condition-1: Checking if all the outputs claimed by this tx are in UTXO pool or not
        // ArrayList<Transaction.Input> txInputs = tx.getInputs();
        // for(Transaction.Input eachInput : txInputs){
            for(int in=0; in <tx.numInputs();in++) {
        //                              System.out.println("prevTxHash ="+eachInput.prevTxHash);
        //                               System.out.println("prevTx Output index ="+eachInput.outputIndex);
            Transaction.Input eachInput = tx.getInput(in);
            UTXO utxo = new UTXO(eachInput.prevTxHash, eachInput.outputIndex);

            if(!uPool.contains(utxo))  // Returns False if atleast 1 utxo is not in the utxoPool
            {   
                //                  System.out.println("Condition 1 failed");
                     return false;    
            }
            
            
            

            // Condition-2: The signature on each input of the transaction is valid 
            // -> Each input has a valid signature or not.. i.e., comparing eachInput's signature 
             // with the signature of the publicKey of the prevTransaction's output whose index is known(from the input tx data.)
        
            Transaction.Output prevTxOutput = uPool.getTxOutput(utxo);
            PublicKey prevOutputPublicKey = prevTxOutput.address;
            byte[] message = tx.getRawDataToSign(in);
            

            if(!(Crypto.verifySignature(prevOutputPublicKey, message ,eachInput.signature))){
                System.out.println("Condition 2 failed");
                    return false; 
            }

            
            
         
            //------------------------------- CONDITION-3 START ---------------------------------------
            // Condition-3:  no UTXO is claimed multiple times by tx.
            int numberOfMatches = 0;
            for(Transaction.Input ip: tx.getInputs()){
                UTXO tempUTXO = new UTXO(ip.prevTxHash, ip.outputIndex);
                if(utxo.equals(tempUTXO))  // If utxo of outerloop has any matches with inner loop temputxo we increment the counter
                {
                    numberOfMatches++;  
                }
            }
            if(numberOfMatches>1) // The number of matches should be exactly 1(Reason is that an UTXO matches with itself once.). If more than 1 we have a double spend, so we return false.
            {
                //                      System.out.println("Condition 3 Failed");
                return false;
            }
        
            //------------------------------- CONDITION-3 END ---------------------------------------


        } // For block close
        //------------------------------- CONDITION-1 END ---------------------------------------

        
        //------------------------------- CONDITION-4 START ---------------------------------------
        // Condition 4: If all the output values are non-negative then condition4 is True. (or if any 1 is negative then we return False)

        ArrayList<Transaction.Output> txOutputs =  tx.getOutputs();
        for (Transaction.Output op : txOutputs) {
            if(op.value<0)
            {   //                          System.out.println("Condition 4 failed");
                return false;
            }
        }
        //------------------------------- CONDITION-4 END ---------------------------------------


        //------------------------------- CONDITION-5 START ---------------------------------------
        // Condition-(5): the sum of {@code tx}s input values is greater than or equal to the sum of its output
        // values; and false otherwise

        // Current transaction(i.e., tx) inputs
        for(Transaction.Input eachInput : tx.getInputs()) {
        UTXO utxo = new UTXO(eachInput.prevTxHash, eachInput.outputIndex);
        Transaction.Output prevTxOutput = uPool.getTxOutput(utxo); // This output is the input to our tx.
        totalInputOfCurrentTx += prevTxOutput.value;
        }

        // Current transaction(i.e., tx) outputs
        for(Transaction.Output eachOutputInCurrentTx : tx.getOutputs()){
            totalOutputOfCurrentTx += eachOutputInCurrentTx.value;
        }
        
        if(totalInputOfCurrentTx < totalOutputOfCurrentTx){
            //                  System.out.println("Condition 5 failed");
            return false;
        }
        //------------------------------- CONDITION-5 END ---------------------------------------
        
       
        
        
        //                      System.out.println("All conditions passed.");
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> validTransactionsList = new ArrayList();  
        Transaction[] validTransactionsArray;       
        for (Transaction tx : possibleTxs){
          

            if(this.isValidTx(tx)) {
                validTransactionsList.add(tx);             
            

             //REMOVE ALL THE UTXO's which are used as inputs to this Transaction
            for (Transaction.Input eachInput : tx.getInputs()){    
            UTXO removeUtxo = new UTXO(eachInput.prevTxHash, eachInput.outputIndex);
            this.uPool.removeUTXO(removeUtxo);
            }
            // ADD ALL THE OUTPUTS OF THIS TRANSACTION TO THE UTXOPOOL
            for(int i=0; i<tx.numOutputs();i++){
                UTXO utxo = new UTXO(tx.getHash(), i );
                this.uPool.addUTXO( utxo , tx.getOutput(i));
            }

        }

        }

        validTransactionsArray = new Transaction[validTransactionsList.size()];
        for (int i=0; i< validTransactionsList.size();  i++){
            validTransactionsArray[i] = validTransactionsList.get(i);
        }
        
        return validTransactionsArray;
    }

}
