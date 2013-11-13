package com.ut.paxos;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Replica extends Process {
    ProcessId[] leaders;
    ProcessId[] clients;
    String logFile;
    int slot_num = 1;
    Map<Integer /* slot number */, Command> proposals = new HashMap<Integer, Command>();
    Map<Integer /* slot number */, Command> decisions = new HashMap<Integer, Command>();
    Map<Command, Integer> readBuffer = new HashMap<Command, Integer>();
    Map<Command, Integer> readCommandAlreadyExecuted = new HashMap<Command, Integer>();
    Map<Command, Integer> commandsAlExecuted = new HashMap<Command, Integer>();

    Set<Account> accounts;

    public Replica(Env env, ProcessId me, ProcessId[] leaders) {
        this.env = env;
        this.me = me;
        this.leaders = leaders;
        this.accounts = new HashSet<Account>();

        env.addProc(me, this);
        this.logFile = "logs/" + me.name.replace(":", "") + ".log";
    }

    void propose(Command c) {
        if (!decisions.containsValue(c)) {
            if (!isReadOnly(c)) {
                for (int s = 1; ; s++) {
                    if (!proposals.containsKey(s) && !decisions.containsKey(s)) {
                        proposals.put(s, c);
                        writeLog(me + " <propose, < slot:  " + s + " command: " + c + " ");
                        for (ProcessId ldr : leaders) {
//                            //TODO Test case 7 REPLICA DOESN'T SEND WRITE REQUEST TO LEADER 1 7th floor
//                            if(ldr.name.equals("leader:1") && c.req_id == 1)
//                                continue;
                            sendMessage(ldr, new ProposeMessage(me, s, c));
                        }
                        break;
                    }
                }
            } else {
                for (ProcessId ldr : leaders) {
                    sendMessage(ldr, new ProposeMessage(me, -1, c));
                }
            }
        }
    }

    void perform(Command c) {
        for (int s = 1; s < slot_num; s++) {
            if (c.equals(decisions.get(s))) {
                slot_num++;
                return;
            }
        }
        String command = (String) c.op;
        AccountAction accountAction = createAccountAction(command);
        if (accountAction != null ) {
            System.out.println("" + me + ": perform " + c);
            writeLog("" + me + ": perform " + c);
            String response;
            response = accountAction.perform();
            commandsAlExecuted.put(c, 1);
            sendMessage(c.client, new ServerResponse(me, command + " " + response, c.req_id));
            executePendingCommands();
        }else{
            sendMessage(c.client, new ServerResponse(me, command + " invalid command", c.req_id));
        }
        slot_num++;

    }

    public void executePendingCommands() {
        Set<Command> commandsExecuted = new HashSet<Command>();

        for (Command command : readBuffer.keySet()) {
            if (readBuffer.get(command) == 0 || decisions.containsKey(readBuffer.get(command))) {
                AccountAction accountAction = createAccountAction((String) command.op);
                String response = accountAction.perform();
                sendMessage(command.client, new ServerResponse(me, command + " " + response, command.req_id));
                //System.err.println(me+" Key Present "+commandsAlExecuted.containsKey(command)+" The key is "+ commandsAlExecuted.get(command));
                if(!commandsAlExecuted.containsKey(command))
                    writeLog(me + " executed " + command);
                commandsAlExecuted.put(command, 1);
                commandsExecuted.add(command);
            }
        }

        for(Command cmd : commandsExecuted)
            readBuffer.remove(cmd);
    }

    private AccountAction createAccountAction(String command) {
        try {
            Account srcaccount = null;
            String[] s = command.split(" ");

            if (s.length > 2 && s[2] != null) {
                srcaccount = getAccountFromNum(Integer.parseInt(s[2]));
                if (srcaccount == null) {
                    System.err.println(me + "Source Account doesn't exist " + Integer.parseInt(s[2]));
                    return null;
                }
            }

            if (srcaccount != null && s[1].equalsIgnoreCase("w")) {
                return new Withdraw(srcaccount, Integer.parseInt(s[3]));
            } else if (srcaccount != null && s[1].equalsIgnoreCase("d")) {
                return new Deposit(srcaccount, Integer.parseInt(s[3]));
            } else if (srcaccount != null && s[1].equalsIgnoreCase("q")) {
                return new Query(srcaccount);
            } else if (srcaccount != null && s[1].equalsIgnoreCase("t")) {
                Account dstaccount = getAccountFromNum(Integer.parseInt(s[3]));
                if (dstaccount == null) {
                    System.err.println("Destination Account doesn't exist");
                    return null;
                }
                return new Transfer(srcaccount, dstaccount, Integer.parseInt(s[4]));
            }

        } catch (Exception e) {
            //e.printStackTrace();
            System.err.println("Invalid Command");
            return null;
        }

        return null;
    }

    private Account getAccountFromNum(int num) {
        Account srcaccount = null;
        for (Account account : accounts) {
            if (account.getAccountNo() == num) {
                srcaccount = account;
                break;
            }
        }
        return srcaccount;
    }

    public void body() {
        System.out.println("Here I am: " + me);
        for (; ; ) {
            PaxosMessage msg = getNextMessage();

            if (msg instanceof RequestMessage) {

                RequestMessage m = (RequestMessage) msg;
                propose(m.command);
            } else if (msg instanceof DecisionMessage) {
                DecisionMessage m = (DecisionMessage) msg;
                decisions.put(m.slot_number, m.command);
                for (; ; ) {
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
            } else if (msg instanceof ReadOnlyCommandMessage) {
                //System.err.println(me + " Read-only for me!");
                ReadOnlyCommandMessage m = (ReadOnlyCommandMessage) msg;
                if (m.minSlot == 0 || decisions.containsKey(m.minSlot)) {
                    String command = (String) m.command.op;
                    AccountAction accountAction = createAccountAction(command);
                    if(!commandsAlExecuted.containsKey(m.command)){
                        String response = accountAction.perform();
                        sendMessage(m.command.client, new ServerResponse(me, command + " " + response, m.command.req_id));
                        writeLog(me + " executed " + m.command);
                        commandsAlExecuted.put(m.command, 1);
                    }
                    //sendMessage(m.src, new ReadOnlyAckMessage(this.me, m.command));
                } else {
                    readBuffer.put(m.command, m.minSlot);
                }
            } else {
                System.err.println("Replica: unknown msg type");
            }
        }
    }

    public boolean isReadOnly(Command command) {
        try {
            String[] s = ((String) (command.op)).split(" ");
            if (s[1].equalsIgnoreCase("q")) {
                return true;
            }
        } catch (Exception e) {
            System.err.println("invalid command");
        }
        return false;
    }

    public void rep_dec() {
        System.out.println("Order of commands executed by replica " + me);
        for (int i = 1; i < decisions.size() + 1; i++) {
            System.out.println("s: " + i + " " + decisions.get(i));
        }
    }

    public void writeLog(String msg) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true));
            bw.write(msg + "\n");
            bw.flush();
        } catch (IOException io) {
            System.err.println(io.getMessage());
        }
    }

}
