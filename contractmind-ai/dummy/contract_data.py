from pydantic import BaseModel, Field


class ContractData(BaseModel):
    is_contract: bool = Field(
        description="True, wenn es sich um einen Vertrag, eine Rechnung oder Police handelt. False bei Spam/Werbung.")
    provider: str = Field(
        description="Der Name des Anbieters (z.B. Telekom, Vattenfall, Allianz). 'Unbekannt', falls nicht auffindbar.")
    contractType: str = Field(description="Die Kategorie (z.B. Internet, Strom, Versicherung, Streaming).")
    monthlyCost: float = Field(
        description="Die monatlichen Kosten in Euro als Zahl. Wenn es ein Jahresbeitrag ist, teile ihn durch 12.")
    endDate: str = Field(
        description="Das Vertragsende oder die nächste Kündigungsfrist im Format YYYY-MM-DD. Wenn unbekannt: '2099-12-31'")