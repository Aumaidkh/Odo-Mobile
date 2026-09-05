package com.hopcape.odo.core.data.support

/**
 * The server halves for a build with no backend.
 *
 * Answering with what they were given is the truthful answer rather than a placeholder: a
 * build with no Supabase credentials has nowhere to push to, and the local row is already
 * durable. The sync marks it SYNCED against a server that does not exist, which is the same
 * thing every other fake data source in this module does.
 *
 * `supabaseModule` replaces all three the moment a build has credentials.
 */
internal class FakeSupportTicketRemoteDataSource : SupportTicketRemoteDataSource {
    override suspend fun push(rows: List<SupportTicketDto>): List<SupportTicketDto> = rows
    override suspend fun fetch(ownerId: String, since: String?): List<SupportTicketDto> = emptyList()
}

internal class FakeIdeaVoteRemoteDataSource : IdeaVoteRemoteDataSource {
    override suspend fun push(rows: List<IdeaVoteDto>): List<IdeaVoteDto> = rows
    override suspend fun fetch(ownerId: String, since: String?): List<IdeaVoteDto> = emptyList()
}

/** No catalogue without a server. The screen leaves the section out rather than showing it empty. */
internal class FakeFeatureIdeaRemoteDataSource : FeatureIdeaRemoteDataSource {
    override suspend fun ideas(): List<FeatureIdeaDto> = emptyList()
}
