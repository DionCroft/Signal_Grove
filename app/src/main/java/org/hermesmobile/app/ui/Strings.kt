package org.hermesmobile.app.ui

import androidx.compose.runtime.*
import org.hermesmobile.app.data.AppPreferences

/** Independent English, Simplified Chinese and accented Hanyu Pinyin entries. */
enum class Words(val en: String, val zh: String, val pin: String) {
    Search("Search","搜索","Sōusuǒ"), LoadMore("Load more conversations","加载更多对话","Jiāzài gèng duō duìhuà"),
    Rename("Rename","重命名","Chóng mìngmíng"), Pin("Pin / unpin locally","本地置顶或取消置顶","Běndì zhìdǐng huò qǔxiāo zhìdǐng"),
    Hide("Hide / show locally","本地隐藏或显示","Běndì yǐncáng huò xiǎnshì"), ShowHidden("Include hidden conversations","包括隐藏的对话","Bāokuò yǐncáng de duìhuà"),
    DeleteServer("Delete on server and this device","删除服务器及本机对话","Shānchú fúwùqì jí běnjī duìhuà"),
    DeleteLocal("Delete from this device","从本机删除","Cóng běnjī shānchú"),
    DeleteSessionConfirm("Delete this conversation? Server deletion permanently removes its history. Pending runs must be resolved first.","删除此对话？服务器删除会永久移除记录。请先处理未完成的运行。","Shānchú cǐ duìhuà? Fúwùqì shānchú huì yǒngjiǔ yíchú jìlù. Qǐng xiān chǔlǐ wèi wánchéng de yùnxíng."),
    ConversationTitle("Conversation title","对话标题","Duìhuà biāotí"), RefreshHistory("Refresh saved history","刷新已存记录","Shuāxīn yǐ cún jìlù"),
    Notes("Notes","备注","Bèizhù"), All("All","全部","Quánbù"), Open("Open","未完成","Wèi wánchéng"),
    Overdue("Overdue","已逾期","Yǐ yúqī"), NoMatches("No matching items.","没有匹配的项目。","Méiyǒu pǐpèi de xiàngmù."),
    JobDetails("View job details","查看定时任务详情","Chákàn dìngshí rènwù xiángqíng"), Enabled("Enabled","已启用","Yǐ qǐyòng"),
    Paused("Paused","已暂停","Yǐ zàntíng"), NextRun("Next scheduled run","下次计划运行","Xià cì jìhuà yùnxíng"),
    LastRun("Last run","上次运行","Shàng cì yùnxíng"), Cached("Saved snapshot · may be out of date","已存快照 · 可能已过期","Yǐ cún kuàizhào · kěnéng yǐ guòqī"),
    LastUpdated("Last refreshed","上次刷新","Shàng cì shuāxīn"),
    Home("Home","首页","Shǒuyè"), Chat("Chat","对话","Duìhuà"), Tasks("Tasks","任务","Rènwù"),
    Automation("Automation","自动化","Zìdònghuà"), Voice("Voice","语音","Yǔyīn"), Settings("Settings","设置","Shèzhì"),
    Assistant("Your everyday companion","你的日常助手","Nǐ de rìcháng zhùshǒu"),
    Greeting("A little space to think.","留一点空间，静心思考。","Liú yìdiǎn kōngjiān, jìngxīn sīkǎo."),
    GreetingBody("Bring a question, make a plan, or pick up where you left off.","提出问题，制定计划，或继续上次的对话。","Tíchū wèntí, zhìdìng jìhuà, huò jìxù shàng cì de duìhuà."),
    Demo("Demo mode · on this device only","演示模式 · 仅限本机","Yǎnshì móshì · jǐn xiàn běnjī"),
    Connected("Connected to Hermes","已连接 Hermes","Yǐ liánjiē Hermes"), Offline("Offline · saved conversations available","离线 · 可查看已保存的对话","Líxiàn · kě chákàn yǐ bǎocún de duìhuà"),
    NewChat("New conversation","新对话","Xīn duìhuà"), Recent("Recent conversations","最近的对话","Zuìjìn de duìhuà"),
    NoChats("Your next conversation starts here.","在这里开始新的对话。","Zài zhèlǐ kāishǐ xīn de duìhuà."),
    Message("Message Hermes…","给 Hermes 发消息…","Gěi Hermes fā xiāoxi…"), Send("Send","发送","Fāsòng"),
    Copy("Copy message","复制消息","Fùzhì xiāoxi"), Copied("Copied","已复制","Yǐ fùzhì"), You("You","你","Nǐ"),
    History("Conversation history","对话记录","Duìhuà jìlù"), Close("Close","关闭","Guānbì"), Cancel("Cancel","取消","Qǔxiāo"),
    Save("Save","保存","Bǎocún"), Delete("Delete","删除","Shānchú"), Edit("Edit","编辑","Biānjí"),
    Refresh("Refresh","刷新","Shuāxīn"), CheckRun("Check / recover run","检查或恢复运行","Jiǎnchá huò huīfù yùnxíng"),
    Retry("Review and resend","检查后重新发送","Jiǎnchá hòu chóngxīn fāsòng"), Stop("Request stop","请求停止","Qǐngqiú tíngzhǐ"), Running("Agent is working","助手正在处理","Zhùshǒu zhèngzài chǔlǐ"),
    Completed("Completed","已完成","Yǐ wánchéng"), Failed("Failed","失败","Shībài"), Cancelled("Cancelled","已取消","Yǐ qǔxiāo"),
    Interrupted("Interrupted","已中断","Yǐ zhōngduàn"), Unknown("Outcome unknown · check before retrying","结果未知 · 重试前请检查","Jiéguǒ wèizhī · chóngshì qián qǐng jiǎnchá"),
    ResponseFinished("Response received · run outcome unavailable","已收到回复 · 无法确认运行结果","Yǐ shōudào huífù · wúfǎ quèrèn yùnxíng jiéguǒ"),
    Approval("Your approval is needed","需要你的批准","Xūyào nǐ de pīzhǔn"), AllowOnce("Allow once","仅允许一次","Jǐn yǔnxǔ yí cì"), Deny("Deny","拒绝","Jùjué"),
    LocalTasks("Local Android tasks","Android 本地任务","Android běndì rènwù"),
    TaskInfo("Personal tasks stay on this device. They are separate from Hermes jobs.","个人任务保存在本机，与 Hermes 定时任务分开。","Gèrén rènwù bǎocún zài běnjī, yǔ Hermes dìngshí rènwù fēnkāi."),
    AddTask("Add task","添加任务","Tiānjiā rènwù"), TaskTitle("Task title","任务名称","Rènwù míngchēng"),
    DueDate("Due date (YYYY-MM-DD, optional)","截止日期（年-月-日，可选）","Jiézhǐ rìqī (nián-yuè-rì, kěxuǎn)"),
    InvalidDate("Enter a valid date, e.g. 2026-11-30.","请输入有效日期，如 2026-11-30。","Qǐng shūrù yǒuxiào rìqī, rú 2026-11-30."),
    Priority("Priority","优先级","Yōuxiānjí"), Low("Low","低","Dī"), Normal("Normal","普通","Pǔtōng"), High("High","高","Gāo"),
    NoTasks("A clear page. Add one thing to do.","从一件小事开始。","Cóng yí jiàn xiǎoshì kāishǐ."),
    CompleteTask("Toggle task completion","切换任务完成状态","Qiēhuàn rènwù wánchéng zhuàngtài"),
    Jobs("Scheduled Hermes jobs","Hermes 定时任务","Hermes dìngshí rènwù"),
    JobsInfo("Read-only view of schedules on your server.","以只读方式查看服务器上的计划。","Yǐ zhǐdú fāngshì chákàn fúwùqì shàng de jìhuà."),
    NoJobs("No scheduled jobs were returned.","暂无定时任务。","Zàn wú dìngshí rènwù."),
    JobsUnsupported("This server does not expose the Jobs API.","此服务器未提供定时任务接口。","Cǐ fúwùqì wèi tígōng dìngshí rènwù jiēkǒu."),
    JobsError("Jobs could not be loaded. Check your connection or token.","无法加载任务，请检查连接或令牌。","Wúfǎ jiāzài rènwù, qǐng jiǎnchá liánjiē huò lìngpái."),
    Schedule("Schedule","计划","Jìhuà"), JobState("Server state","服务器状态","Fúwùqì zhuàngtài"), Prompt("Prompt","提示词","Tíshìcí"),
    VoiceInfo("Tap to speak. Review your words before sending.","点击说话，发送前可检查文字。","Diǎnjī shuōhuà, fāsòng qián kě jiǎnchá wénzì."),
    VoiceDisclosure("Android's speech recognition service may send audio online. Listening ends when you leave this screen or the app.","Android 语音识别服务可能在线处理音频。离开此页面或应用后停止录音。","Android yǔyīn shíbié fúwù kěnéng zàixiàn chǔlǐ yīnpín. Líkāi cǐ yèmiàn huò yìngyòng hòu tíngzhǐ lùyīn."),
    TapSpeak("Tap to speak","点击说话","Diǎnjī shuōhuà"), Listening("Listening · tap to finish","正在聆听 · 点击结束","Zhèngzài língtīng · diǎnjī jiéshù"),
    Processing("Processing speech…","正在识别…","Zhèngzài shíbié…"), Transcript("Transcription preview","转写预览","Zhuǎnxiě yùlǎn"),
    VoiceUnavailable("Speech recognition is unavailable on this device.","此设备不支持语音识别。","Cǐ shèbèi bù zhīchí yǔyīn shíbié."),
    VoiceError("Speech was not recognised. Check microphone permission and try again.","未能识别语音，请检查麦克风权限后重试。","Wèi néng shíbié yǔyīn, qǐng jiǎnchá màikèfēng quánxiàn hòu chóngshì."),
    Server("Hermes connection","Hermes 连接","Hermes liánjiē"), ServerUrl("Server URL","服务器地址","Fúwùqì dìzhǐ"), Token("Bearer token","访问令牌","Fǎngwèn lìngpái"),
    TokenHint("Leave blank to keep the token for this saved URL.","留空以保留此地址的已存令牌。","Liúkōng yǐ bǎoliú cǐ dìzhǐ de yǐ cún lìngpái."),
    Connect("Save & test connection","保存并测试连接","Bǎocún bìng cèshì liánjiē"), Forget("Forget saved server and token","删除已存服务器和令牌","Shānchú yǐ cún fúwùqì hé lìngpái"),
    ServerHelp("Use your server's trusted HTTPS or VPN address. Port 8642 is the default. On Android, 127.0.0.1 means this phone.","请使用服务器的可信 HTTPS 或 VPN 地址。默认端口为 8642。在 Android 上，127.0.0.1 指手机本身。","Qǐng shǐyòng fúwùqì de kěxìn HTTPS huò VPN dìzhǐ. Mòrèn duānkǒu wéi 8642. Zài Android shàng, 127.0.0.1 zhǐ shǒujī běnshēn."),
    DebugHttp("Debug only: allow local HTTP testing","仅调试：允许本地 HTTP 测试","Jǐn tiáoshì: yǔnxǔ běndì HTTP cèshì"),
    Appearance("Make it yours","个性化外观","Gèxìnghuà wàiguān"), Theme("Theme","主题","Zhǔtí"),
    Preview("Live preview","实时预览","Shíshí yùlǎn"), Language("Interface language","界面语言","Jièmiàn yǔyán"),
    Chinese("Show Simplified Chinese","显示简体中文","Xiǎnshì jiǎntǐ Zhōngwén"), Pinyin("Show accented pinyin","显示带声调的拼音","Xiǎnshì dài shēngdiào de pīnyīn"),
    Accessibility("Accessibility","无障碍","Wú zhàng'ài"), ReducedMotion("Reduce motion","减少动画","Jiǎnshǎo dònghuà"),
    About("About & security","关于与安全","Guānyú yǔ ānquán"),
    AboutBody("Hermes Agent Mobile · {version}. An independent community client, not endorsed by NousResearch. Conversations are stored in app-private storage; the bearer token is encrypted with Android Keystore. Device backups are disabled.","Hermes Agent Mobile · {version}。独立社区客户端，未经 NousResearch 官方认可。对话保存在应用私有存储中；令牌由 Android Keystore 加密。设备备份已禁用。","Hermes Agent Mobile · {version}. Dúlì shèqū kèhùduān, wèi jīng NousResearch guānfāng rènkě. Duìhuà bǎocún zài yìngyòng sīyǒu cúnchǔ zhōng; lìngpái yóu Android Keystore jiāmì. Shèbèi bèifèn yǐ jìnyòng."),
    Security("Your token can grant powerful server access. Use a private VPN or trusted HTTPS. Approvals always require your explicit choice.","令牌可能授予强大的服务器权限。请使用私有 VPN 或可信 HTTPS。批准操作始终需要你明确选择。","Lìngpái kěnéng shòuyǔ qiángdà de fúwùqì quánxiàn. Qǐng shǐyòng sīyǒu VPN huò kěxìn HTTPS. Pīzhǔn cāozuò shǐzhōng xūyào nǐ míngquè xuǎnzé."),
    Github("Source on GitHub","GitHub 源代码","GitHub yuán dàimǎ"), Licences("Open-source licences","开源许可证","Kāiyuán xǔkězhèng"),
    ClearChats("Clear local conversation cache","清除本地对话缓存","Qīngchú běndì duìhuà huǎncún"),
    ClearConfirm("Delete cached conversations on this device? Server conversations and local tasks remain available.","删除本机对话缓存？服务器对话和本地任务仍会保留。","Shānchú běnjī duìhuà huǎncún? Fúwùqì duìhuà hé běndì rènwù réng huì bǎoliú."),
    Dismiss("Dismiss","关闭提示","Guānbì tíshì"), Loading("Loading…","正在加载…","Zhèngzài jiāzài…")
}
val LocalPreferences=staticCompositionLocalOf { AppPreferences() }
fun Words.localized(p: AppPreferences): String = buildList { add(en); if(p.chinese) { add(zh); if(p.pinyin) add(pin) } }.joinToString("\n")
@Composable fun label(w: Words) = w.localized(LocalPreferences.current)
fun statusWord(status: String) = when(status) {
    "completed" -> Words.Completed; "failed" -> Words.Failed; "cancelled" -> Words.Cancelled; "interrupted" -> Words.Interrupted
    "unknown" -> Words.Unknown; "response_finished" -> Words.ResponseFinished; "waiting_for_approval" -> Words.Approval
    "running","submitting","queued" -> Words.Running; else -> Words.Chat
}
