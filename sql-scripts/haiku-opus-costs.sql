select sum(chat_message.prompt_tokens)/1000000.0,
       5*sum(chat_message.completion_tokens)/1000000.0,
       5*sum(chat_message.opus_completion_tokens)/1000000.0,
       25*sum(chat_message.opus_prompt_tokens)/1000000.0
from chat_message