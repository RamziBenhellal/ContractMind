from fastapi import APIRouter
from openai import BaseModel

from agents.chat_agent import ChatAgent

router = APIRouter(prefix="/api/chat", tags=["Chat"])
ai_agent = ChatAgent()

class ChatRequest(BaseModel):
    question: str
    context: str

@router.post("/ask")
async def ask_chat(payload: ChatRequest):
    try:
        answer = ai_agent.ask_question(payload.question, payload.context)
        print(answer)
        return {"answer": answer}
    except Exception as e:
        print(f"Failed to Answer: {e}")
