package com.ichimoku.screener.dto;

public class IchimokuValues {
    private double tenkanSen;
    private double kijunSen;
    private double senkouSpanA;
    private double senkouSpanB;
    private Double chikouSpan;

    public double getCloudTop() {
        return Math.max(senkouSpanA, senkouSpanB);
    }

    public double getCloudBottom() {
        return Math.min(senkouSpanA, senkouSpanB);
    }

    public double getTenkanSen() { return tenkanSen; }
    public void setTenkanSen(double tenkanSen) { this.tenkanSen = tenkanSen; }
    public double getKijunSen() { return kijunSen; }
    public void setKijunSen(double kijunSen) { this.kijunSen = kijunSen; }
    public double getSenkouSpanA() { return senkouSpanA; }
    public void setSenkouSpanA(double senkouSpanA) { this.senkouSpanA = senkouSpanA; }
    public double getSenkouSpanB() { return senkouSpanB; }
    public void setSenkouSpanB(double senkouSpanB) { this.senkouSpanB = senkouSpanB; }
    public Double getChikouSpan() { return chikouSpan; }
    public void setChikouSpan(Double chikouSpan) { this.chikouSpan = chikouSpan; }
}
