package com.redis.autoscaler.poller;

import com.redis.autoscaler.HttpClientConfig;
import com.redis.autoscaler.RedisCloudDatabase;
import com.redis.autoscaler.documents.Rule;
import com.redis.autoscaler.documents.Rule$;
import com.redis.autoscaler.documents.RuleRepository;
import com.redis.autoscaler.metrics.PrometheusMetrics;
import com.redis.autoscaler.services.RedisCloudDatabaseService;
import com.redis.om.spring.annotations.ReducerFunction;
import com.redis.om.spring.search.stream.EntityStream;
import com.redis.om.spring.tuple.Single;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.net.http.HttpClient;

@Component
@EnableScheduling
public class PrometheusExtrasPoller {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(PrometheusExtrasPoller.class);
    private final PrometheusMetrics prometheusMetrics;
    private final RedisCloudDatabaseService redisCloudDatabaseService;
    private final EntityStream entityStream;

    public PrometheusExtrasPoller(PrometheusMetrics prometheusMetrics, RedisCloudDatabaseService redisCloudDatabaseService, EntityStream entityStream) {
        this.prometheusMetrics = prometheusMetrics;
        this.redisCloudDatabaseService = redisCloudDatabaseService;
        this.entityStream = entityStream;
    }


    @Scheduled(fixedRate = 10000)
    public void pollDbConfiguredThroughput(){
        try{
            List<Single<List<String>>> dbIdsRes = entityStream.of(Rule.class).groupBy().reduce(ReducerFunction.TOLIST, Rule$.DB_ID).toList(String.class);
            if(dbIdsRes.size() == 0 || ((List<String>)dbIdsRes.get(0).get(0)).size() == 0){
                return;
            }

            List<String> dbIds = (List<String>)dbIdsRes.get(0).get(0);
            for (String dbId: dbIds){
                RedisCloudDatabase db = redisCloudDatabaseService.getDatabase(dbId);
                prometheusMetrics.addConfiguredThroughput(dbId, db.getPrivateEndpoint(), db.getThroughputMeasurement().getValue());
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

}
