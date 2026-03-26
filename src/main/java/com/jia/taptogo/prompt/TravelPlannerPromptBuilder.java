package com.jia.taptogo.prompt;

import com.jia.taptogo.model.TripPlanRequest;
import org.springframework.stereotype.Component;

@Component
public class TravelPlannerPromptBuilder {

    public String buildSystemPrompt() {
        return """
                你是一名资深中文旅行规划师，默认服务对象是中文用户，输出语言必须且只能是简体中文。
                任务要求：
                1. 根据用户给出的目的地、出行方式和天数，生成一份真实、可执行、按地理位置和时间顺序合理安排的旅行计划。
                2. 所有内容必须使用自然的简体中文表达，避免英文模板化文案。
                3. 行程内容要贴合目的地本地特色，优先推荐当地有代表性的景点、街区、餐饮区域和体验。
                4. 每天的活动安排要避免跨城、跨大区乱跳，单日移动距离要合理，交通建议要和用户选择的出行方式一致。
                5. 美食建议要体现当地特色，不要泛泛而谈，也不要推荐没有旅行意义的普通连锁快餐。
                6. 需要为行程中的每个地点提供大致准确的经纬度字段 latitude 和 longitude，方便前端地图展示。
                7. 对值得推荐的活动或地点，生成对应的小红书搜索链接，放入 social_link 字段。
                8. 严格输出 JSON，不要输出 Markdown，不要输出解释说明，不要输出代码块。
                9. 所有描述字段都要具体、自然，说明为什么值得去、适合什么时间段、典型玩法是什么。
                """;
    }

    public String buildUserPrompt(TripPlanRequest request) {
        return """
                用户需求：
                - 目的地：%s
                - 出行方式：%s
                - 游玩天数：%d 天

                输出约束：
                1. 所有文案默认使用简体中文。
                2. 住宿推荐写“适合住在哪个区域”，不要只写酒店名。
                3. 每天至少包含上午、下午、晚上三个时间段中的两个，并保证活动衔接自然。
                4. 如果是中国城市，请优先使用中国用户熟悉的表达方式，例如“地铁”“打车”“步行”“商圈”“老城区”“景区周边”。
                5. 餐饮活动要体现本地特色，不要推荐普通连锁快餐或旅行意义很弱的地点。

                请严格输出以下 JSON 结构：
                {
                  "trip_summary": "一句话概括这次旅行的风格和亮点",
                  "total_days": %d,
                  "recommended_accommodation": {
                    "area": "推荐住宿区域",
                    "reason": "为什么住这里更合适"
                  },
                  "daily_itinerary": [
                    {
                      "day": 1,
                      "theme": "当天主题",
                      "activities": [
                        {
                          "time": "上午/下午/晚上",
                          "type": "景点/美食/交通/休闲",
                          "name": "具体地点或活动名称，必须使用中文",
                          "description": "具体玩法、看点或安排逻辑",
                          "transit_tip": "从上一个点位到这里的交通建议",
                          "social_link": "对应的小红书搜索链接",
                          "latitude": 具体地点的大致纬度,
                          "longitude": 具体地点的大致经度
                        }
                      ]
                    }
                  ]
                }
                """.formatted(request.destination(), request.travelMode(), request.days(), request.days());
    }
}
