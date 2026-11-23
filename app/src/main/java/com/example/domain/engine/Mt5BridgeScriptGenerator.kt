package com.example.domain.engine

import com.example.domain.model.BrokerCredentials

object Mt5BridgeScriptGenerator {

    fun generatePythonMt5BridgeScript(creds: BrokerCredentials): String {
        val broker = creds.mt5BrokerName.ifBlank { "XM Global" }
        val server = creds.mt5Server.ifBlank { "XMGlobal-MT5" }
        val login = creds.mt5Login.ifBlank { "12345678" }
        val secret = creds.apiToken.trim().ifBlank {
            creds.localApiKey.ifBlank { "nsp_change_this_bridge_secret" }
        }
        val suffix = creds.mt5SymbolSuffix.trim()

        return """
# ==============================================================================
# Neto Scalper Pro — Custom MT5 REST Bridge API (neto_mt5_bridge.py)
# Broker: $broker | Server: $server | Login: $login
# Run on Windows PC / Windows VPS alongside MetaTrader 5 terminal:
#   pip install fastapi uvicorn MetaTrader5 pydantic
#   python neto_mt5_bridge.py
# ==============================================================================

import os
from datetime import datetime, timezone
from typing import Optional

import MetaTrader5 as mt5
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel
import uvicorn

API_SECRET = os.getenv("NETO_BRIDGE_SECRET", "$secret")
DEFAULT_SERVER = "$server"
DEFAULT_LOGIN = "$login"
SYMBOL_SUFFIX = "$suffix"

app = FastAPI(title="Neto Scalper Pro MT5 Bridge", version="1.0.0")


def verify_auth(authorization: Optional[str] = Header(default=None)):
    if authorization != f"Bearer {API_SECRET}":
        raise HTTPException(status_code=401, detail="Invalid Bearer token")


def ensure_mt5(login: Optional[str] = None, server: Optional[str] = None):
    if not mt5.initialize():
        raise HTTPException(status_code=503, detail=f"MT5 initialize failed: {mt5.last_error()}")
    target_login = (login or DEFAULT_LOGIN).strip()
    target_server = (server or DEFAULT_SERVER).strip()
    info = mt5.account_info()
    if info is None and target_login.isdigit():
        pwd = os.getenv("MT5_PASSWORD", "")
        if pwd:
            mt5.login(int(target_login), password=pwd, server=target_server)
            info = mt5.account_info()
    if info is None:
        raise HTTPException(status_code=503, detail="MT5 terminal not logged in")
    return info


TIMEFRAME_MAP = {
    "1m": mt5.TIMEFRAME_M1,
    "5m": mt5.TIMEFRAME_M5,
    "15m": mt5.TIMEFRAME_M15,
}


class OrderRequest(BaseModel):
    login: str = ""
    server: str = ""
    symbol: str = "XAUUSD$suffix"
    side: str = "BUY"
    volume: float = 0.02
    sl: float = 0.0
    tp: float = 0.0
    comment: str = "Neto Scalper Pro"


class CloseRequest(BaseModel):
    positionId: str


@app.get("/mt5/account", dependencies=[Depends(verify_auth)])
def get_account(login: str = "", server: str = ""):
    info = ensure_mt5(login, server)
    return {
        "broker": "$broker",
        "currency": info.currency,
        "server": info.server,
        "balance": float(info.balance),
        "equity": float(info.equity),
        "margin": float(info.margin),
        "freeMargin": float(info.margin_free),
        "leverage": int(info.leverage),
        "login": int(info.login),
        "type": "ACCOUNT_TRADE_MODE_REAL" if info.trade_mode == mt5.ACCOUNT_TRADE_MODE_REAL else "ACCOUNT_TRADE_MODE_DEMO",
    }


@app.get("/mt5/quote", dependencies=[Depends(verify_auth)])
def get_quote(symbol: str = "XAUUSD$suffix"):
    ensure_mt5()
    mt5.symbol_select(symbol, True)
    tick = mt5.symbol_info_tick(symbol)
    if tick is None:
        raise HTTPException(status_code=404, detail=f"Symbol {symbol} tick unavailable")
    return {
        "symbol": symbol,
        "bid": float(tick.bid),
        "ask": float(tick.ask),
        "time": datetime.fromtimestamp(tick.time, tz=timezone.utc).isoformat(),
    }


@app.get("/mt5/candles", dependencies=[Depends(verify_auth)])
def get_candles(symbol: str = "XAUUSD$suffix", timeframe: str = "1m", count: int = 70):
    ensure_mt5()
    mt5.symbol_select(symbol, True)
    tf = TIMEFRAME_MAP.get(timeframe.lower(), mt5.TIMEFRAME_M1)
    rates = mt5.copy_rates_from_pos(symbol, tf, 0, min(max(count, 20), 200))
    if rates is None:
        return []
    return [
        {
            "time": datetime.fromtimestamp(int(r["time"]), tz=timezone.utc).isoformat(),
            "open": float(r["open"]),
            "high": float(r["high"]),
            "low": float(r["low"]),
            "close": float(r["close"]),
            "tickVolume": int(r["tick_volume"]),
        }
        for r in rates
    ]


@app.get("/mt5/positions", dependencies=[Depends(verify_auth)])
def get_positions():
    ensure_mt5()
    positions = mt5.positions_get() or []
    return [
        {
            "id": str(p.ticket),
            "symbol": p.symbol,
            "type": "POSITION_TYPE_BUY" if p.type == mt5.POSITION_TYPE_BUY else "POSITION_TYPE_SELL",
            "volume": float(p.volume),
            "openPrice": float(p.price_open),
            "currentPrice": float(p.price_current),
            "stopLoss": float(p.sl) if p.sl > 0 else None,
            "takeProfit": float(p.tp) if p.tp > 0 else None,
            "profit": float(p.profit),
            "time": datetime.fromtimestamp(int(p.time), tz=timezone.utc).isoformat(),
            "comment": p.comment,
        }
        for p in positions
    ]


@app.post("/mt5/order", dependencies=[Depends(verify_auth)])
def place_order(req: OrderRequest):
    ensure_mt5(req.login, req.server)
    mt5.symbol_select(req.symbol, True)
    tick = mt5.symbol_info_tick(req.symbol)
    if tick is None:
        raise HTTPException(status_code=400, detail=f"Tick unavailable for {req.symbol}")
    is_buy = req.side.upper() == "BUY"
    order_type = mt5.ORDER_TYPE_BUY if is_buy else mt5.ORDER_TYPE_SELL
    price = tick.ask if is_buy else tick.bid
    request = {
        "action": mt5.TRADE_ACTION_DEAL,
        "symbol": req.symbol,
        "volume": float(req.volume),
        "type": order_type,
        "price": float(price),
        "sl": float(req.sl) if req.sl > 0 else 0.0,
        "tp": float(req.tp) if req.tp > 0 else 0.0,
        "deviation": 25,
        "magic": 202601,
        "comment": req.comment[:30],
        "type_time": mt5.ORDER_TIME_GTC,
        "type_filling": mt5.ORDER_FILLING_IOC,
    }
    res = mt5.order_send(request)
    if res is None or res.retcode != mt5.TRADE_RETCODE_DONE:
        msg = res.comment if res is not None else str(mt5.last_error())
        raise HTTPException(status_code=400, detail=f"Order rejected: {msg}")
    return {
        "numericCode": int(res.retcode),
        "stringCode": "TRADE_RETCODE_DONE",
        "message": "Executed",
        "orderId": str(res.order),
        "positionId": str(res.deal),
    }


@app.post("/mt5/close", dependencies=[Depends(verify_auth)])
def close_position(req: CloseRequest):
    ensure_mt5()
    ticket = int(req.positionId)
    matches = mt5.positions_get(ticket=ticket)
    if not matches:
        raise HTTPException(status_code=404, detail="Position not found")
    pos = matches[0]
    tick = mt5.symbol_info_tick(pos.symbol)
    is_buy = pos.type == mt5.POSITION_TYPE_BUY
    close_type = mt5.ORDER_TYPE_SELL if is_buy else mt5.ORDER_TYPE_BUY
    price = tick.bid if is_buy else tick.ask
    res = mt5.order_send({
        "action": mt5.TRADE_ACTION_DEAL,
        "symbol": pos.symbol,
        "volume": float(pos.volume),
        "type": close_type,
        "position": pos.ticket,
        "price": float(price),
        "deviation": 25,
        "magic": 202601,
        "comment": "Neto Close",
        "type_time": mt5.ORDER_TIME_GTC,
        "type_filling": mt5.ORDER_FILLING_IOC,
    })
    if res is None or res.retcode != mt5.TRADE_RETCODE_DONE:
        raise HTTPException(status_code=400, detail="Close failed")
    return {"numericCode": int(res.retcode), "message": "Closed", "orderId": str(res.order)}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8080)
""".trimIndent()
    }
}
