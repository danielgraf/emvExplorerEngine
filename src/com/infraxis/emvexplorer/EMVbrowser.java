/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.infraxis.emvexplorer;

/**
 *
 * @author dgraf
 */
public class EMVbrowser {
    
    private String cardData;
    private String status;
    private String state;
    private String readerid;
    private String cardlabel;

    public String getReaderid() {
        return readerid;
    }

    public void setReaderid(String readerid) {
        this.readerid = readerid;
    }

    public String getCardlabel() {
        return cardlabel;
    }

    public void setCardlabel(String cardlabel) {
        this.cardlabel = cardlabel;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCardData() {
        return cardData;
    }

    public void setCardData(String cardData) {
        this.cardData = cardData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
    public EMVbrowser()
    {
        cardData = "";
        status = "Please insert a card";
        state = "info";
        
        Thread scc = new Thread(new cardReader(this));
        scc.start();
        
        
    }
    
    public void reset()
    {
        setCardData("");
        setCardlabel("");
        setReaderid("");
    }
    
    
    
    
}
