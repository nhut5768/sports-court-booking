package vn.uth.court;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

/** Bounded background AI jobs. Never retries or falls back to another provider. */
@Component
public class UnifiedChatAi {
  private final AiIntentClient ai;
  private final CourtAssistantService assistant;
  private final CourtMessagingService chat;
  private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12),task->{var t=new Thread(task,"unified-chat-ai");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
  private final java.time.LocalDateTime startedAt=java.time.LocalDateTime.now(AppConstants.ZONE);
  private long window=System.currentTimeMillis();private int global;
  private final Map<Long,Integer> perUser=new HashMap<>();
  public UnifiedChatAi(AiIntentClient ai,CourtAssistantService assistant,CourtMessagingService chat){this.ai=ai;this.assistant=assistant;this.chat=chat;}
  private synchronized boolean permit(long user){long now=System.currentTimeMillis();if(now-window>=60000){window=now;global=0;perUser.clear();}if(global>=40||perUser.getOrDefault(user,0)>=8)return false;global++;perUser.merge(user,1,Integer::sum);return true;}
  @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
  public void requested(CourtMessagingService.AiRequested event){
    if(!ai.available()||!permit(event.customerId())){chat.finishAi(event.conversationId(),event.sourceId(),null);return;}
    try{workers.execute(()->process(event));}catch(RejectedExecutionException ex){chat.finishAi(event.conversationId(),event.sourceId(),null);}
  }
  void process(CourtMessagingService.AiRequested event){
    try{
      var context=chat.aiContext(event.conversationId(),event.sourceId());if(context==null)return;
      var intent=ai.interpret(context.history(),context.message(),assistant.today());
      chat.finishAi(event.conversationId(),event.sourceId(),assistant.answer(intent,context.ownerId()));
    }catch(Exception ex){try{chat.finishAi(event.conversationId(),event.sourceId(),null);}catch(Exception ignored){}}
  }
  @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
  public void recover(){
    // Never leave a fake "AI is replying" state after a process restart. No paid retry.
    for(int batch=0;batch<10;batch++){
      var abandoned=chat.abandonedAi(startedAt);if(abandoned.isEmpty())return;
      for(var event:abandoned)chat.finishAi(event.conversationId(),event.sourceId(),null);
    }
  }
  boolean idle(){return workers.getActiveCount()==0 && workers.getQueue().isEmpty();}
  @PreDestroy void stop(){workers.shutdownNow();}
}
