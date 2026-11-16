package dev.xiaoman.mcp

import dev.xiaoman.api.AgentEvent
import dev.xiaoman.api.AgentListener
import dev.xiaoman.api.ToolApprovalGateway
import dev.xiaoman.api.ToolApprovalRequest
import dev.xiaoman.api.UserChannel

class UserChannelApprovals(
    private val users: UserChannel,
    /** Required for the wait to be visible: the question is announced before the channel blocks. */
    private val listener: AgentListener? = null,
) : ToolApprovalGateway {
    override suspend fun approve(request: ToolApprovalRequest): Boolean {
        val arguments = request.argumentsJson.let { if (it.length > 1000) it.take(1000) + "[参数已截断]" else it }
        val question = "要调用「${request.title ?: request.tool}」（${request.server}），参数：$arguments。${request.reason}。$CONFIRM_MARKER。"
        listener?.onEvent(AgentEvent.NeedsUser(request.taskId, question, takeover = false))
        val reply = users.ask(request.taskId, question, takeover = false)
        return reply.trim().lowercase() in setOf("确认", "是", "好", "可以", "y", "yes")
    }

    companion object {
        /** Marks a yes/no question, so the chat screen can offer 确认 / 取消 buttons instead of typing. */
        const val CONFIRM_MARKER = "回复“确认”继续，其他回复取消"
    }
}
