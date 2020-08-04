import java.security.PublicKey;
import java.util.*;


class MaxFeeTxHandler{
    UTXOPool uPool;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.uPool = utxoPool;
    }

    public boolean isValidTx(Transaction tx) {
        double totalInputOfCurrentTx = 0.0;
        double totalOutputOfCurrentTx = 0.0;
        
        //------------------------------- CONDITION-1 START ---------------------------------------
        // Condition-1: Checking if all the outputs claimed by this tx are in UTXO pool or not
            for(int in=0; in <tx.numInputs();in++) {
            Transaction.Input eachInput = tx.getInput(in);
            UTXO utxo = new UTXO(eachInput.prevTxHash, eachInput.outputIndex);
            if(!uPool.contains(utxo))  // Returns False if atleast 1 utxo is not in the utxoPool
            {   
                //                  System.out.println("Condition 1 failed");
                     return false;    
            }
           
            /*
            Condition-2: The signature on each input of the transaction is valid 
            -> Each input has a valid signature or not.. i.e., comparing eachInput's signature 
             with the signature of the publicKey of the prevTransaction's output whose index is known(from the input tx data.)
            */      
            Transaction.Output prevTxOutput = uPool.getTxOutput(utxo);
            PublicKey prevOutputPublicKey = prevTxOutput.address;
            byte[] message = tx.getRawDataToSign(in);
            

            if(!(Crypto.verifySignature(prevOutputPublicKey, message ,eachInput.signature))){
                System.out.println("Condition 2 failed");
                    return false; 
            }
 
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
        
           


        } // For block close
       
        // Condition 4: If all the output values are non-negative then condition4 is True. (or if any 1 is negative then we return False)

        ArrayList<Transaction.Output> txOutputs =  tx.getOutputs();
        for (Transaction.Output op : txOutputs) {
            if(op.value<0)
            {   //                          System.out.println("Condition 4 failed");
                return false;
            }
        }
   
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
       
        
        //                      System.out.println("All conditions passed.");
        return true;
    }

    public double calculateTxFees(Transaction tx){
        double inputFees = 0.0;
        double outputFees = 0.0;
        
        if(!isValidTx(tx)) {
            return -1;
        }

        for(Transaction.Input eachInput : tx.getInputs()) {
            UTXO utxo = new UTXO(eachInput.prevTxHash, eachInput.outputIndex);
            
            Transaction.Output prevTxOutput = uPool.getTxOutput(utxo); // This output is the input to our tx.
            inputFees += prevTxOutput.value;
            }
            // Current transaction(i.e., tx) outputs
            for(Transaction.Output eachOutputInCurrentTx : tx.getOutputs()){
                outputFees += eachOutputInCurrentTx.value;
            }
            return (inputFees - outputFees);
        }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        
        List<Double> txFeesValuesList = new ArrayList();  
        List<Transaction> validTransactionsList = new ArrayList();  
        
        for(Transaction eachTx: possibleTxs){
            txFeesValuesList.add(calculateTxFees(eachTx));
        }

        while (true){
        double max = -999.0;
        int maxIndex  = -10;
        double temp;
        for(int i=0; i < txFeesValuesList.size();i++)
        {
            temp = txFeesValuesList.get(i);
            if(temp > max ) {
                max = temp;
                maxIndex = i;
            }     
        }
        if(max == -999.0 &&  maxIndex == -10){
            break;
        }
        if(max == -1){
            break;
        }
        Transaction eachTx = possibleTxs[maxIndex];
        if(isValidTx(eachTx)){

            txFeesValuesList.set(maxIndex, -99999.0);
            validTransactionsList.add(eachTx);             
             //REMOVE ALL THE UTXO's which are used as inputs to this Transaction
            for (Transaction.Input eachInput : eachTx.getInputs()){    
            UTXO toRemoveUtxo = new UTXO(eachInput.prevTxHash, eachInput.outputIndex);
            this.uPool.removeUTXO(toRemoveUtxo);
            }
            // ADD ALL THE OUTPUTS OF THIS TRANSACTION TO THE UTXOPOOL
            for(int i=0; i<eachTx.numOutputs();i++){
                UTXO utxo = new UTXO(eachTx.getHash(), i );
                this.uPool.addUTXO( utxo , eachTx.getOutput(i));
            } 
           
            
           // for all the values that are -1 in the txFeesValuesList we recompute the tx fees.

           





            

          }
          else{
            txFeesValuesList.set(maxIndex, -1.0);  // Means that the tx has few values which were invalid during that point of execution
            // for(int z=0; z<txFeesValuesList.size();z++){
            //     if(txFeesValuesList.get(z)==-1){
            //         txFeesValuesList.set(z, calculateTxFees(possibleTxs[z]));
            //     }
            // }
          }

          for(int z=0; z<txFeesValuesList.size();z++){
            if(txFeesValuesList.get(z)==-1.0){
                txFeesValuesList.set(z, calculateTxFees(possibleTxs[z]));
            }
        }

        } // While loop closed

        for (int j=0; j < txFeesValuesList.size();j++)
        {
            if(txFeesValuesList.get(j) == -1){
                Transaction eachTx = possibleTxs[j];
                if(isValidTx(eachTx)){
                    txFeesValuesList.set(j, -99999.0);
        
                    validTransactionsList.add(eachTx);             
                     //REMOVE ALL THE UTXO's which are used as inputs to this Transaction
                    for (Transaction.Input eachInput : eachTx.getInputs()){    
                    UTXO toRemoveUtxo = new UTXO(eachInput.prevTxHash, eachInput.outputIndex);
                    this.uPool.removeUTXO(toRemoveUtxo);
                    }
                    // ADD ALL THE OUTPUTS OF THIS TRANSACTION TO THE UTXOPOOL
                    for(int i=0; i<eachTx.numOutputs();i++){
                        UTXO utxo = new UTXO(eachTx.getHash(), i );
                        this.uPool.addUTXO( utxo , eachTx.getOutput(i));
                    } 
            }
        }
    }
   
        Transaction[] validTransactionsArray = new Transaction[validTransactionsList.size()];
        for (int i=0; i< validTransactionsList.size();  i++){
            validTransactionsArray[i] = validTransactionsList.get(i);
        }
        return validTransactionsArray;
    }
}