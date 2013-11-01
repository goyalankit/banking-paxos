package com.ut.paxos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Replica extends Process {
	ProcessId[] leaders;
	int slot_num = 1;
	Map<Integer /* slot number */, Command> proposals = new HashMap<Integer, Command>();
	Map<Integer /* slot number */, Command> decisions = new HashMap<Integer, Command>();

    Set<Account> accounts;

	public Replica(Env env, ProcessId me, ProcessId[] leaders){
		this.env = env;
		this.me = me;
		this.leaders = leaders;
        this.accounts = new HashSet<Account>();
		env.addProc(me, this);
	}

	void propose(Command c){
		if (!decisions.containsValue(c)) {
			for (int s = 1;; s++) {
				if (!proposals.containsKey(s) && !decisions.containsKey(s)) {
					proposals.put(s, c);
					for (ProcessId ldr: leaders) {
						sendMessage(ldr, new ProposeMessage(me, s, c));
					}
					break;
				}
			}
		}
	}

	void perform(Command c){
		for (int s = 1; s < slot_num; s++) {
			if (c.equals(decisions.get(s))) {
				slot_num++;
				return;
			}
		}
        String command = (String) c.op;
        AccountAction accountAction = createAccountAction(command);
        if(accountAction != null)
        {
            System.out.println("" + me + ": perform " + c);
            accountAction.perform();
        }
		slot_num++;

	}

    private AccountAction createAccountAction(String command){
        try{
            Account srcaccount = null;
            String []s = command.split(" ");

            if( s.length > 2 && s[2] != null)
            {
                srcaccount = getAccountFromNum(Integer.parseInt(s[2]));
                if(srcaccount == null){
                    System.err.println("Source Account doesn't exist "+Integer.parseInt(s[2]));
                        return null;
                }
            }

            if(srcaccount != null && s[1].equalsIgnoreCase("w"))
            {
                return new Withdraw(srcaccount, Integer.parseInt(s[3]));
            }
            else if(srcaccount != null && s[1].equalsIgnoreCase("d"))
            {
                return new Deposit(srcaccount, Integer.parseInt(s[3]));
            }
            else if(srcaccount != null && s[1].equalsIgnoreCase("q"))
            {
                return new Query(srcaccount);
            }
            else if(srcaccount != null && s[1].equalsIgnoreCase("t"))
            {
                Account dstaccount = getAccountFromNum(Integer.parseInt(s[3]));
                if(dstaccount == null){
                    System.err.println("Destination Account doesn't exist");
                    return null;
                }
                return new Transfer(srcaccount, dstaccount, Integer.parseInt(s[4]));
            }

        }catch (Exception e){
            e.printStackTrace();
            System.err.println("Invalid Command");
            return null;
        }
        return null;
    }

    private Account getAccountFromNum(int num){
        Account srcaccount = null;
        for(Account account:accounts){
            if(account.getAccountNo() == num){
                srcaccount = account;
                break;
            }
        }
        return srcaccount;
    }

	public void body(){
		System.out.println("Here I am: " + me);
		for (;;) {
			PaxosMessage msg = getNextMessage();

			if (msg instanceof RequestMessage) {
				RequestMessage m = (RequestMessage) msg;
				propose(m.command);
			}

			else if (msg instanceof DecisionMessage) {
				DecisionMessage m = (DecisionMessage) msg;
				decisions.put(m.slot_number, m.command);
				for (;;) {
					Command c = decisions.get(slot_num);
					if (c == null) {
						break;
					}
					Command c2 = proposals.get(slot_num);
					if (c2 != null && !c2.equals(c)) {
						propose(c2);
					}
					perform(c);
				}
			}
			else {
				System.err.println("Replica: unknown msg type");
			}
		}
	}
}
