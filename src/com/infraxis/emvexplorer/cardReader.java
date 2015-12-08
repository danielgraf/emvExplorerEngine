/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.infraxis.emvexplorer;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import sasc.emv.EMVSession;
import sasc.emv.EMVTerminal;
import sasc.smartcard.common.CardSession;
import sasc.smartcard.common.Context;
import sasc.smartcard.common.SessionProcessingEnv;
import sasc.smartcard.common.SmartCard;
import sasc.terminal.CardConnection;
import sasc.terminal.Terminal;
import sasc.terminal.TerminalAPIManager;
import sasc.terminal.TerminalException;
import sasc.terminal.TerminalProvider;
import sasc.util.Log;

/**
 *
 * @author dgraf
 */
public class cardReader implements Runnable {

    private SmartCard smartCard;
    private TerminalProvider terminalProvider;
    private EMVbrowser app;
    private boolean read;
    private HashMap<String, Boolean> readcards;

    public cardReader(EMVbrowser a) {
        smartCard = null;
        terminalProvider = null;
        app = a;
        read = false;
        readcards = new HashMap<>();

    }

    private boolean initCardReader() {
        System.out.println("INIT CARD READER");
        try {
            Context.init();
            terminalProvider = TerminalAPIManager.getProvider(TerminalAPIManager.SelectionPolicy.ANY_PROVIDER);
        } catch (TerminalException ex) {
            Logger.getLogger(cardReader.class.getName()).log(Level.SEVERE, null, ex);
            app.setStatus("Exception : " + ex.getMessage());
            app.setState("danger");
            app.setCardData("");
            terminalProvider = null;
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        while (true) {

            if (terminalProvider == null) {
                if (!initCardReader()) {
                    System.out.println("INIT CARD READER FAILED");

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(cardReader.class.getName()).log(Level.SEVERE, null, ex);
                        app.setStatus(ex.getMessage());
                        app.setState("danger");
                        app.reset();
                    }
                }
            } else {

                try {
                    if (terminalProvider.listTerminals().isEmpty()) {

                        app.setStatus("There are no card readers detected");
                        app.setState("warning");
                        app.reset();
                        initCardReader();

                        Thread.sleep(500);
                    } else {

                       // app.setMessage("There are " + terminalProvider.listTerminals().size() + " card readers attached");

                        for (Terminal terminal : terminalProvider.listTerminals()) {
                            // If this terminal has a card in it and we haven't transmitted it
                            if (terminal.isCardPresent() && readcards.get(terminal.getName()) == null) {
                                // Register it
                                System.out.println("Transmitting card insertion " + terminal.getName());
                                try {
                                    app.setStatus("Reading card...");
                                    app.setState("warning");
                                    app.setCardData(readCard(terminal));
                                    readcards.put(terminal.getName(), true);
                                    app.setState("success");
                                    app.setStatus("Card read successfully");
                                    app.setCardlabel(smartCard.getSelectedApplication().getLabel() + " " + smartCard.getSelectedApplication().getPAN().getPanAsString());
                                    app.setReaderid(terminal.getName());
                                    read = true;
                                } catch (Exception ex) {
                                    Logger.getLogger(cardReader.class.getName()).log(Level.SEVERE, null, ex);
                                    app.setStatus(ex.getMessage());
                                    app.setState("danger");
                                    app.reset();
                                }

                            }
                            if (!terminal.isCardPresent() && readcards.get(terminal.getName()) != null) {
                                app.setStatus("Please insert a card");
                                app.setState("info");
                                app.reset();
                                
                                read = false;
                                readcards.remove(terminal.getName());

                            }

                        }

                        Thread.sleep(500);

                    }
                } catch (TerminalException | InterruptedException | IllegalArgumentException ex) {
                    

                    app.setStatus("Exception : " + ex.getMessage());
                    app.setState("danger");
                    app.reset();
                   
                }

            }
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Logger.getLogger(cardReader.class.getName()).log(Level.SEVERE, null, ex);
                app.setStatus(ex.getMessage());
                app.setState("danger");
                app.reset();
            }

        }

    }
    
    private String readCard(Terminal terminal) throws Exception
    {
        try {
            CardConnection cc = terminal.connect();
            
            if(cc == null){
                throw new Exception("Could not connect to card on reader " + terminal.getName());
            }

            SessionProcessingEnv env = new SessionProcessingEnv();
            env.setReadMasterFile(true);
            env.setProbeAllKnownAIDs(true);

            CardSession cardSession = CardSession.createSession(cc, env);

            smartCard = cardSession.initCard();
            
            EMVSession session = EMVSession.startSession(smartCard, cc);

            session.initContext();
            smartCard.getEmvApplications().stream().forEach((application) -> {
                try{ //If the processing of this app fails, just skip it
                    session.selectApplication(application);
                    session.initiateApplicationProcessing(); //GET PROCESSING OPTIONS + READ RECORD(s)

                    if (!application.isInitializedOnICC()) {
                        //Skip if GPO failed (might not be a EMV card, or conditions not satisfied)
                        
                    }

                    //Be VERY CAREFUL when setting this, as it WILL block the application if the PIN Try Counter reaches 0
                    //Must be combined with a PIN callback handler
                    EMVTerminal.setDoVerifyPinIfRequired(false);
                    session.prepareTransactionProcessing();

                    //Check if the transaction processing skipped some steps
                    if(application.getATC() == -1 || application.getLastOnlineATC() == -1) {
                        session.testReadATCData(); //ATC, Last Online ATC
                    }
                    //If PIN Try Counter has not been read, try to read it
                    if(application.getPINTryCounter() == -1) {
                        session.readPINTryCounter();
                    }
                    if(!application.isTransactionLogProcessed()) {
                        session.checkForTransactionLogRecords();
                    }

                    //testGetChallenge (see if the app supports generating an unpredictable number)
                    session.testGetChallenge();
                    
                    
                    
                } catch(Exception e) {
                    e.printStackTrace(System.err);
                    Log.info(String.format("Error processing app: %s. Skipping app: %s", e.getMessage(), application.toString()));
                }
            });
            
            
            if (smartCard.getSelectedApplication() != null)
            {
                return smartCard.toString();
            }
            
            return "Card could not be read";
        } catch (TerminalException ex) {
            Logger.getLogger(cardReader.class.getName()).log(Level.SEVERE, null, ex);
            return ex.getMessage();
        }
    }

}
