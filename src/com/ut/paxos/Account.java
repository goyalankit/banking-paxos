package com.ut.paxos;

import java.io.Serializable;

public class Account implements Serializable{
    private int accountNo;
    private int currentBalance;

    public Account(int clientn, int currentBalance) {
        this.accountNo = clientn;
        this.currentBalance = currentBalance;
    }

    public void perform(AccountAction action){
        if(action instanceof Withdraw){
            Withdraw wdAction = (Withdraw)action;
            if(currentBalance > wdAction.getWdamount()){
                currentBalance = currentBalance - wdAction.getWdamount();
                System.out.println("AccountAction: "+ accountNo +" amount of "+wdAction.getWdamount()+ " withdrawn");
            }else{
                System.out.println("AccountAction: Withdraw Failed. Insufficient Balance");
            }
        }
    }

    public int getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(int accountNo) {
        this.accountNo = accountNo;
    }

    public int getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(int currentBalance) {
        this.currentBalance = currentBalance;
    }

    public String toString(){
        return "Account(" + accountNo + ", " + currentBalance + ")";
    }
}
