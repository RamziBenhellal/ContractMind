from fastapi import APIRouter, Query
from fastapi.responses import JSONResponse

from models.fints import ConfirmTanRequest, SelectTanMethodRequest, StartConnectionRequest, SyncRequest
from services.fints.connector import FinTsConnector
from services.fints.errors import FinTsError

router = APIRouter(tags=["FinTS"])
connector = FinTsConnector()


@router.post("/bank-connection")
def start_bank_connection(payload: StartConnectionRequest):
    return connector.start_connection(payload)


@router.post("/bank-connection/confirm-tan")
def confirm_bank_connection(payload: ConfirmTanRequest):
    session_id = payload.connection_id
    if not session_id:
        raise FinTsError("TAN_INVALID", "connectionId fehlt.", 400)
    return connector.confirm_tan(session_id, payload.tan_method_id, payload.tan)


@router.get("/bank-connection/{connection_id}/transactions")
def get_bank_transactions(connection_id: str):
    fetched = connector.fetch_transactions(connection_id)
    return fetched.model_dump(by_alias=True, mode="json")


@router.get("/fints/banks/search")
def search_banks(query: str = Query(..., min_length=2)):
    return connector.search_banks(query)


@router.post("/fints/sessions")
def start_fints_session(payload: StartConnectionRequest):
    return connector.start_connection(payload)


@router.post("/fints/sessions/{session_id}/tan-method")
def select_fints_tan_method(session_id: str, payload: SelectTanMethodRequest):
    return connector.select_tan_method(session_id, payload.tan_method_id)


@router.post("/fints/sessions/{session_id}/confirm-tan")
def confirm_fints_session(session_id: str, payload: ConfirmTanRequest):
    return connector.confirm_tan(session_id, payload.tan_method_id, payload.tan)


@router.post("/fints/sync")
def sync_fints(payload: SyncRequest):
    return {"accounts": connector.sync(payload.blz, payload.login_id, payload.pin, payload.fints_url)}


def register_exception_handlers(app) -> None:
    @app.exception_handler(FinTsError)
    async def fints_error_handler(_request, exc: FinTsError):
        return JSONResponse(
            status_code=exc.status_code,
            content={"code": exc.code, "message": exc.message},
        )
