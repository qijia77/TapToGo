package com.jia.taptogo.service;

import com.jia.taptogo.model.AiTripDraft;
import com.jia.taptogo.model.TripPlanRequest;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DemoTripDraftService {

    private static final Map<String, CityTemplate> TEMPLATES = buildTemplates();

    public AiTripDraft generate(TripPlanRequest request) {
        CityTemplate template = TEMPLATES.get(normalizeDestination(request.destination()));
        if (template != null) {
            return template.toDraft(request.days());
        }
        return buildGenericDraft(request);
    }

    private static AiTripDraft buildGenericDraft(TripPlanRequest request) {
        List<AiTripDraft.DayPlan> days = new ArrayList<>();
        for (int i = 1; i <= request.days(); i++) {
            days.add(new AiTripDraft.DayPlan(
                    i,
                    i == 1 ? "抵达后熟悉" + request.destination() : "围绕" + request.destination() + "的顺路安排",
                    List.of(
                            activity(
                                    "上午",
                                    "景点",
                                    request.destination() + "主地标区",
                                    "先从城市辨识度最高、步行可串联的地标片区开始，优先安排 2 到 3 个相邻点位，避免第一天拉得太散。",
                                    "优先使用" + request.travelMode() + "到主地标区后再步行串联，上午尽量控制在同一片区。",
                                    null,
                                    null
                            ),
                            activity(
                                    "下午",
                                    "美食",
                                    request.destination() + "老城口碑餐馆街区",
                                    "把午餐和附近街区留逛放在一起，选择本地游客都会去的老店或市场，比单纯找商场更容易吃到代表性风味。",
                                    "午餐后不急着跨区，继续在周边步行消化掉一个街区或一处小型展馆。",
                                    null,
                                    null
                            ),
                            activity(
                                    "晚上",
                                    "休闲",
                                    request.destination() + "夜景步道或河岸区域",
                                    "晚上安排夜景、散步和轻松收尾，留出弹性给返程、休息或临时加点，不把最后一段塞得过满。",
                                    "夜间尽量选择回酒店顺路的区域，避免深夜长距离折返。",
                                    null,
                                    null
                            )
                    )
            ));
        }

        return new AiTripDraft(
                "这是一份以地标、步行串联和本地餐饮为主的城市短途行程，优先保证每一天都能落到具体片区里执行。",
                request.days(),
                new AiTripDraft.Accommodation(
                        "市中心或主交通枢纽附近",
                        "住在换乘方便的区域，临时改计划时成本更低，也更容易把上午和晚上的路线接起来。"
                ),
                days
        );
    }

    private static Map<String, CityTemplate> buildTemplates() {
        Map<String, CityTemplate> templates = new LinkedHashMap<>();
        register(templates, tokyoTemplate(), "东京", "tokyo");
        register(templates, kyotoTemplate(), "京都", "kyoto");
        register(templates, osakaTemplate(), "大阪", "osaka");
        register(templates, seoulTemplate(), "首尔", "seoul");
        register(templates, shanghaiTemplate(), "上海", "shanghai");
        register(templates, beijingTemplate(), "北京", "beijing");
        register(templates, chengduTemplate(), "成都", "chengdu");
        register(templates, xianTemplate(), "西安", "xian", "xi'an");
        return Map.copyOf(templates);
    }

    private static void register(Map<String, CityTemplate> templates, CityTemplate template, String... aliases) {
        for (String alias : aliases) {
            templates.put(normalizeDestination(alias), template);
        }
    }

    private static String normalizeDestination(String destination) {
        if (destination == null) {
            return "";
        }
        String normalized = Normalizer.normalize(destination, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("　", "");
        if (normalized.endsWith("市")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static CityTemplate tokyoTemplate() {
        return new CityTemplate(
                "这份东京行程优先把浅草、上野、涩谷、新宿这些第一次去也最容易走顺的区域串起来，节奏紧凑但不空泛。",
                new AiTripDraft.Accommodation(
                        "上野站、东京站或新宿站周边",
                        "这几个区域换乘效率高，去浅草、涩谷、新宿和机场方向都方便，适合短天数行程。"
                ),
                List.of(
                        day("初识东京",
                                activity("上午", "景点", "浅草寺", "第一站放在浅草寺和雷门，适合一到东京就快速进入城市氛围，仲见世通也方便顺手买伴手礼。", "优先坐地铁到浅草站，出站后步行串联雷门、仲见世通和浅草寺。", 35.7148, 139.7967),
                                activity("下午", "美食", "上野阿美横町", "午后转去上野公园和阿美横町，把博物馆、公园散步和街边小吃放在同一段，密度高但不累。", "从浅草搭银座线或步行接一段地铁到上野，下午尽量不再跨到西东京。", 35.7089, 139.7745),
                                activity("晚上", "休闲", "涩谷十字路口", "晚上去涩谷看十字路口和街头夜景，适合拍照、逛店和吃一顿节奏快但不踩雷的晚饭。", "从上野直接坐山手线到涩谷，晚饭和夜景放在同一片区收尾。", 35.6595, 139.7005)
                        ),
                        day("西东京经典线",
                                activity("上午", "景点", "明治神宫", "上午先去明治神宫和代代木一带，避开中午以后的人流，步行环境也更舒服。", "建议地铁或山手线到原宿站，从神宫外苑步行进入。", 35.6764, 139.6993),
                                activity("下午", "休闲", "表参道", "中午后顺着表参道和原宿街区慢慢逛，咖啡店、选物店和街景都集中，不需要频繁换线。", "从明治神宫步行过渡到表参道，下午保持在同一片区。", 35.6655, 139.7126),
                                activity("晚上", "景点", "东京都厅展望台", "傍晚去新宿看城市天际线，天气好时夜景非常稳，也适合把晚饭放在都厅附近。", "表参道坐副都心线或山手线到新宿，晚上不再临时跨区。", 35.6896, 139.6917)
                        ),
                        day("湾岸与夜景",
                                activity("上午", "景点", "丰洲 teamLab Planets 周边", "如果想看东京更现代的一面，上午放在丰洲最顺，沉浸式展馆和湾岸步道适合连着安排。", "建议提前预约后再出发，地铁到丰洲后步行完成上午行程。", 35.6454, 139.7896),
                                activity("下午", "美食", "银座", "下午回到银座补一顿更稳妥的日式午餐，再顺手逛百货和文具店，适合带伴手礼。", "丰洲到银座地铁直达，午餐和逛街继续放在同一个商业区。", 35.6717, 139.7650),
                                activity("晚上", "休闲", "东京塔", "晚上去东京塔或芝公园一带收尾，夜景辨识度高，比最后回酒店发呆更有记忆点。", "银座到东京塔车程不长，夜景后直接回酒店。", 35.6586, 139.7454)
                        ),
                        day("东京延展日",
                                activity("上午", "景点", "谷中银座", "如果天数更多，可以把谷中银座和旧街区安排进来，东京日常感更强，也适合慢一点走。", "早上地铁到日暮里后步行进入谷中片区。", 35.7278, 139.7668),
                                activity("下午", "美食", "神乐坂", "下午去神乐坂找法日融合的小街和餐馆，街巷密度高，适合边走边吃。", "从谷中银座转山手线加地铁到饭田桥，下午集中在神乐坂内部步行。", 35.7017, 139.7353),
                                activity("晚上", "休闲", "六本木新城展望台", "晚上去六本木看东京塔方向夜景，适合作为长线东京行程的收束。", "神乐坂到六本木换乘不复杂，晚间直接留在展望台和商场周边。", 35.6604, 139.7292)
                        )
                )
        );
    }

    private static CityTemplate kyotoTemplate() {
        return new CityTemplate(
                "这份京都安排把东山、伏见稻荷、岚山这些第一次到京都最值得走的区域拆开，避免一天横跳。",
                new AiTripDraft.Accommodation(
                        "京都站或四条河原町周边",
                        "京都站适合进出城，四条河原町适合夜间吃饭和步行收尾，短住都很稳。"
                ),
                List.of(
                        day("东山初体验",
                                activity("上午", "景点", "清水寺", "上午先去清水寺，能避开中午后的旅行团高峰，顺路把二年坂三年坂一起走完。", "建议尽早出门，公交或出租到清水寺区域后步行下行。", 34.9948, 135.7850),
                                activity("下午", "美食", "祇园花见小路", "下午留在祇园和花见小路一带吃午饭、看町屋街景，比跨到西边更顺。", "从清水寺一路下坡步行到祇园，基本不用额外折返。", 35.0037, 135.7788),
                                activity("晚上", "休闲", "鸭川纳凉步道", "晚上回到鸭川边散步收尾，河岸夜景和周边小餐馆都适合慢慢结束第一天。", "祇园步行到鸭川即可，夜里不建议再跨区。", 35.0051, 135.7708)
                        ),
                        day("伏见稻荷与市区补位",
                                activity("上午", "景点", "伏见稻荷大社", "伏见稻荷越早越值得去，鸟居通道在上午体验最好，也更容易拍到干净画面。", "京都站坐 JR 奈良线到稻荷站，出站就是参道。", 34.9671, 135.7727),
                                activity("下午", "美食", "锦市场", "下午回市区去锦市场，把京都小吃和伴手礼集中处理掉，不把晚上塞得太满。", "从伏见稻荷回京都站后换地铁到四条，下午步行逛锦市场。", 35.0050, 135.7644),
                                activity("晚上", "休闲", "先斗町", "晚上把晚饭留给先斗町或木屋町，更适合坐下来好好吃一顿。", "锦市场步行可达先斗町，晚间活动尽量收在河原町片区。", 35.0074, 135.7702)
                        ),
                        day("岚山慢行",
                                activity("上午", "景点", "岚山竹林小径", "岚山适合单独拿出半天到一天，竹林、天龙寺和渡月桥集中安排会比夹在中间更舒服。", "建议一早前往岚山，先走竹林小径再接天龙寺。", 35.0170, 135.6713),
                                activity("下午", "景点", "渡月桥", "下午顺着渡月桥和保津川一带慢慢走，节奏比市区明显更松，适合放在行程后段。", "岚山区域内部以步行为主，不建议下午再赶回东山。", 35.0094, 135.6777),
                                activity("晚上", "美食", "四条河原町", "晚上回四条河原町吃饭，补一顿更稳妥的居酒屋或京料理。", "岚山坐阪急或 JR 回市区，晚饭放在酒店附近更省力。", 35.0033, 135.7681)
                        )
                )
        );
    }

    private static CityTemplate osakaTemplate() {
        return new CityTemplate(
                "这份大阪路线把城景、商圈和夜间街头感拆开，适合第一次去又不想全程只逛商场的人。",
                new AiTripDraft.Accommodation(
                        "难波、心斋桥或梅田周边",
                        "这几个区域晚上吃饭方便，去大阪城、道顿堀和机场方向也都顺。"
                ),
                List.of(
                        day("大阪城市初识",
                                activity("上午", "景点", "大阪城公园", "上午先去大阪城公园和天守阁，开场气势足，也适合把第一天的步行强度放在白天。", "地铁到谷町四丁目或森之宫后步行进入公园。", 34.6873, 135.5259),
                                activity("下午", "美食", "心斋桥", "下午去心斋桥补午饭和逛街，道顿堀可以一起走完，不用频繁换线。", "从大阪城坐地铁到心斋桥，下午集中在南区。", 34.6745, 135.5010),
                                activity("晚上", "休闲", "道顿堀", "晚上一定留给道顿堀，招牌夜景、章鱼烧和街头气氛都适合第一次来大阪。", "心斋桥步行即可进入道顿堀，晚饭和夜景一并完成。", 34.6687, 135.5023)
                        ),
                        day("老大阪与展望线",
                                activity("上午", "景点", "新世界", "上午去新世界和通天阁，能看到和梅田完全不同的老大阪气质。", "地铁到动物园前站，下车后步行串联新世界和通天阁。", 34.6525, 135.5063),
                                activity("下午", "美食", "黑门市场", "下午转去黑门市场补一顿海鲜或熟食，比把午饭塞在景区门口更稳。", "从新世界到黑门市场距离不远，适合继续留在南区活动。", 34.6654, 135.5063),
                                activity("晚上", "景点", "梅田蓝天大厦", "晚上去梅田看高空夜景，作为第二天收尾会比继续挤在道顿堀更有变化。", "黑门市场或难波坐御堂筋线到梅田，夜景后直接回酒店。", 34.7055, 135.4896)
                        )
                )
        );
    }

    private static CityTemplate seoulTemplate() {
        return new CityTemplate(
                "这份首尔行程优先把景福宫、北村、广藏市场、明洞和汉江这些首次到访最稳妥的区域串起来。",
                new AiTripDraft.Accommodation(
                        "明洞、乙支路入口或弘大周边",
                        "明洞和乙支路适合第一次到首尔，弘大更年轻，但两边出行都比较方便。"
                ),
                List.of(
                        day("首尔历史线",
                                activity("上午", "景点", "景福宫", "上午先去景福宫和光化门，宫殿区适合白天光线，也便于连着走北村。", "地铁到景福宫站，先看宫殿再往北村方向步行。", 37.5796, 126.9770),
                                activity("下午", "休闲", "北村韩屋村", "下午在北村和三清洞慢走，比赶场式打卡更适合首尔第一天。", "从景福宫步行上坡到北村，不建议中途跨江。", 37.5826, 126.9830),
                                activity("晚上", "美食", "广藏市场", "晚饭放在广藏市场，适合一次性吃到首尔街头代表性的几样小吃。", "北村转一段地铁到钟路五街，晚上留在市中心收尾。", 37.5704, 127.0009)
                        ),
                        day("商业区与夜景",
                                activity("上午", "休闲", "圣水洞", "上午去圣水洞看首尔更新后的创意街区，咖啡馆和买手店密度高，适合慢慢逛。", "建议地铁直达圣水站，上午集中在街区内部步行。", 37.5445, 127.0557),
                                activity("下午", "美食", "明洞", "下午回明洞补午饭和购物，第一次去首尔这里依然是效率最高的综合区。", "圣水回明洞换乘清晰，下午不再切到太远的商圈。", 37.5636, 126.9827),
                                activity("晚上", "景点", "N 首尔塔", "晚上上南山看首尔夜景，辨识度高，也适合把第二天收在一个明确的大场景里。", "从明洞上山方式很多，缆车和公交都可以，夜景后直接回酒店。", 37.5512, 126.9882)
                        )
                )
        );
    }

    private static CityTemplate shanghaiTemplate() {
        return new CityTemplate(
                "这份上海路线把外滩、豫园、武康路、新天地和陆家嘴拆开，确保每天都能落到具体街区和点位。",
                new AiTripDraft.Accommodation(
                        "人民广场、南京西路或静安寺周边",
                        "住在市中心更容易串联黄浦江两岸，白天夜里切换也快。"
                ),
                List.of(
                        day("黄浦江初识",
                                activity("上午", "景点", "外滩", "上午先走外滩和周边老建筑，白天看立面细节比晚上更适合拍照。", "地铁到南京东路后步行前往外滩，上午基本不需要换区。", 31.2400, 121.4900),
                                activity("下午", "美食", "豫园", "下午去豫园和城隍庙一带吃本帮点心、看老城厢街巷，路线衔接自然。", "从外滩步行或打一小段车到豫园，下午继续留在黄浦。", 31.2273, 121.4924),
                                activity("晚上", "休闲", "陆家嘴", "晚上过江去陆家嘴看天际线，第一天就能把上海最有辨识度的夜景拿下。", "傍晚从豫园转地铁到陆家嘴，夜景后直接回酒店。", 31.2354, 121.5015)
                        ),
                        day("法租界慢走",
                                activity("上午", "休闲", "武康路", "上午放在武康路和安福路，适合边走边看老洋房和小店，不用赶节奏。", "地铁到交通大学或上海图书馆，之后以步行为主。", 31.2051, 121.4370),
                                activity("下午", "美食", "新天地", "下午去新天地补午饭和逛街，和法租界气质相近，衔接顺。", "武康路打一小段车到新天地，下午继续在市中心活动。", 31.2210, 121.4740),
                                activity("晚上", "休闲", "思南公馆", "晚上留在思南路和复兴公园周边，比硬赶第二个大景点更舒服。", "新天地步行或短打车即可到思南公馆。", 31.2152, 121.4687)
                        )
                )
        );
    }

    private static CityTemplate beijingTemplate() {
        return new CityTemplate(
                "这份北京行程把故宫中轴线、颐和园和三里屯拆开，兼顾第一次来北京必须看到的东西和实际体力。",
                new AiTripDraft.Accommodation(
                        "东四、王府井或国贸周边",
                        "这几个区域去故宫、三里屯和机场方向都顺，短途住起来最省心。"
                ),
                List.of(
                        day("中轴线初体验",
                                activity("上午", "景点", "故宫博物院", "北京第一天先给故宫和午门一线，越早进越值得，不然人流和体力都会一起上来。", "建议提前预约并尽早入场，地铁到天安门东或东华门周边后步行。", 39.9163, 116.3972),
                                activity("下午", "景点", "景山公园", "下午去景山看故宫全景，再顺着北海或什刹海补一段，不用再跨到西郊。", "从故宫北门出来步行到景山，下午保持在同一片区。", 39.9324, 116.3961),
                                activity("晚上", "美食", "南锣鼓巷", "晚上去南锣鼓巷和鼓楼周边吃饭，选择更多，也比景区门口更容易找到顺眼的店。", "景山到南锣鼓巷打车或骑行都方便，晚上直接留在东城收尾。", 39.9375, 116.4039)
                        ),
                        day("皇家园林与夜生活",
                                activity("上午", "景点", "颐和园", "上午单独给颐和园，面积大、步行多，拆开安排比硬塞进下午更合理。", "建议一早出发，地铁到北宫门后进入园区。", 39.9999, 116.2755),
                                activity("下午", "休闲", "圆明园", "如果体力还够，下午补圆明园或清华北大外侧路线，整个下午都留在西北片区。", "颐和园到圆明园移动距离短，不建议中途回市中心。", 40.0084, 116.3010),
                                activity("晚上", "美食", "三里屯", "晚上回三里屯吃饭放松，和白天园林形成节奏反差，也方便买东西。", "西北片区结束后直接打车回东三环，晚间活动不要再加第二站。", 39.9336, 116.4548)
                        )
                )
        );
    }

    private static CityTemplate chengduTemplate() {
        return new CityTemplate(
                "这份成都安排把宽窄巷子、人民公园、文殊院、玉林路这些适合第一次到成都的区域拆开，吃和逛能走顺。",
                new AiTripDraft.Accommodation(
                        "春熙路、太古里或文殊院周边",
                        "住在这几个区域，去老城区、餐馆和夜生活片区都很方便。"
                ),
                List.of(
                        day("老城慢热线",
                                activity("上午", "景点", "人民公园", "成都第一天适合从人民公园开始，喝茶、看本地人活动，比一上来就猛冲景区更贴近城市节奏。", "地铁到人民公园站后步行进园，上午都留在少城片区。", 30.6597, 104.0555),
                                activity("下午", "美食", "奎星楼街", "下午去奎星楼街和宽窄巷子附近吃午饭，选择密度高，也方便接着逛。", "人民公园到奎星楼街步行或短打车即可。", 30.6677, 104.0518),
                                activity("晚上", "休闲", "宽窄巷子", "晚上回宽窄巷子看夜景和街巷灯光，适合第一天轻松收尾。", "下午继续留在少城，不需要来回折返。", 30.6673, 104.0492)
                        ),
                        day("寺院与夜生活",
                                activity("上午", "景点", "文殊院", "上午去文殊院，更适合安静地看古建和寺院节奏，也方便顺路找素斋或茶馆。", "地铁到文殊院站，上午行程以步行为主。", 30.6737, 104.0740),
                                activity("下午", "美食", "建设巷", "下午去建设巷或周边找小吃，适合补成都更街头的一面。", "从文殊院打车到建设巷，下午不再硬切景点。", 30.6760, 104.1011),
                                activity("晚上", "休闲", "玉林路", "晚上留给玉林路，适合吃火锅、烧烤或者去小酒馆片区慢慢坐。", "建设巷到玉林路车程可控，晚上就在南边片区收尾。", 30.6380, 104.0602)
                        )
                )
        );
    }

    private static CityTemplate xianTemplate() {
        return new CityTemplate(
                "这份西安路线把城墙、回民街、大雁塔和博物馆拆开，适合第一次到西安又不想只剩打卡的人。",
                new AiTripDraft.Accommodation(
                        "钟楼、永宁门或小寨周边",
                        "住在南门到钟楼一线，去城墙、回民街、大雁塔和地铁都比较顺。"
                ),
                List.of(
                        day("古城第一印象",
                                activity("上午", "景点", "永宁门", "上午先从永宁门上城墙，能迅速建立对西安古城格局的感受，也不会像午后那样晒。", "地铁到永宁门站后步行进城墙，上午尽量留在南门片区。", 34.2431, 108.9471),
                                activity("下午", "美食", "回民街", "下午把回民街和钟楼鼓楼周边放在一起，边吃边逛更合理。", "从南门回到钟楼片区可步行或骑行，下午不要再去大雁塔。", 34.2655, 108.9472),
                                activity("晚上", "休闲", "钟楼", "晚上看钟楼夜景后顺路收尾，第一天不宜再加太远的夜场。", "回民街散完步行就能到钟楼，晚上直接回酒店。", 34.2595, 108.9471)
                        ),
                        day("博物馆与夜游",
                                activity("上午", "景点", "陕西历史博物馆", "陕西历史博物馆值得单独给一个上午，馆藏密度高，赶着看会很可惜。", "建议提前预约，地铁到小寨后步行前往。", 34.2256, 108.9604),
                                activity("下午", "景点", "大雁塔", "下午接大雁塔和大慈恩寺，和博物馆在同一片区，顺路程度高。", "博物馆出来步行或短打车去大雁塔即可。", 34.2186, 108.9630),
                                activity("晚上", "休闲", "大唐不夜城", "晚上把大唐不夜城留出来慢慢走，灯光和街头演出都适合放在第二天。", "大雁塔步行就能接到大唐不夜城，晚间不再跨回老城。", 34.2144, 108.9688)
                        )
                )
        );
    }

    private static DayTemplate day(String theme, AiTripDraft.Activity... activities) {
        return new DayTemplate(theme, List.of(activities));
    }

    private static AiTripDraft.Activity activity(
            String time,
            String type,
            String name,
            String description,
            String transitTip,
            Double latitude,
            Double longitude
    ) {
        return new AiTripDraft.Activity(
                time,
                type,
                name,
                description,
                transitTip,
                xiaohongshuSearchLink(name),
                latitude,
                longitude
        );
    }

    private static String xiaohongshuSearchLink(String keyword) {
        return "https://www.xiaohongshu.com/search_result?keyword="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    private record DayTemplate(String theme, List<AiTripDraft.Activity> activities) {
        private AiTripDraft.DayPlan toDayPlan(int dayNumber, boolean extended) {
            String themeLabel = extended ? theme + "（延展）" : theme;
            return new AiTripDraft.DayPlan(dayNumber, themeLabel, activities);
        }
    }

    private record CityTemplate(
            String summary,
            AiTripDraft.Accommodation accommodation,
            List<DayTemplate> days
    ) {
        private AiTripDraft toDraft(int requestedDays) {
            List<AiTripDraft.DayPlan> itinerary = new ArrayList<>();
            for (int i = 0; i < requestedDays; i++) {
                DayTemplate template = days.get(i % days.size());
                itinerary.add(template.toDayPlan(i + 1, i >= days.size()));
            }
            return new AiTripDraft(summary, requestedDays, accommodation, itinerary);
        }
    }
}
