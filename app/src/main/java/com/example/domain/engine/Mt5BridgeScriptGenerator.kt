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
# Neto Master — Gold & Bitcoin MT5 Live Signal Bridge (neto_mt5_bridge.py)
# Broker: $broker | Server: $server | Login: $login
# Run alongside MetaTrader 5 terminal:
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

app = FastAPI(title="Neto Master MT5 Bridge", version="2.0.0")


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
    "30m": mt5.TIMEFRAME_M30,
    "1h": mt5.TIMEFRAME_H1,
    "4h": mt5.TIMEFRAME_H4,
    "1d": mt5.TIMEFRAME_D1,
}


class OrderRequest(BaseModel):
    login: str = ""
    server: str = ""
    symbol: str = "XAUUSD$suffix"
    side: str = "BUY"
    volume: float = 0.01
    sl: float = 0.0
    tp: float = 0.0
    comment: str = "Neto Master"


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
        "magic": 202602,
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


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8080)
""".trimIndent()
    }
}
