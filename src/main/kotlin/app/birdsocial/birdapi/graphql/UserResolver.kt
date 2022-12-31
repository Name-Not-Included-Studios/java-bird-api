package app.birdsocial.birdapi.graphql

import app.birdsocial.birdapi.EnvironmentData
import app.birdsocial.birdapi.graphql.exceptions.ThrottleRequestException
import app.birdsocial.birdapi.graphql.types.*
import app.birdsocial.birdapi.services.AuthService
import org.neo4j.ogm.session.SessionFactory
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.*
import kotlin.system.measureTimeMillis

@Controller
class UserResolver(val sessionFactory: SessionFactory, val authService: AuthService) {

//    @QueryMapping
//    fun searchUsers(query: UserSearchCriteria): List<User> {}

    @QueryMapping
    fun getMe(): User {}

    @QueryMapping
    fun getRefreshToken: String {}

    @MutationMapping
    fun login(userLogin: AuthInput): Pair<String, String> {
        if (!EnvironmentData.bucket.tryConsume(50))
            throw ThrottleRequestException("You are sending too many requests, please wait and try again.")

        val token: Pair<String, String>

        val time = measureTimeMillis {
            token = authService.login(userLogin)
        }
        println("Login User : ${time}ms")

        return token
    }

    @MutationMapping
    fun createProfile(user: ProfileInput): User {}

    @MutationMapping
    fun createAccount(auth: AuthInput): LoginResponse {}

    @MutationMapping
    fun updateUser(user: ProfileInput): User {}

    @MutationMapping
    fun deleteUser(): User {}

    @MutationMapping
    fun createPost(post: PostInput): Post {}

    @MutationMapping
    fun deletePost(postId: UUID): Post {}

    @MutationMapping
    fun annotatePost(post: PostInput): Post {}

    @MutationMapping
    fun followUser(followerId: UUID): User {}

    @MutationMapping
    fun unfollowUser(followerId: UUID): User {}

    @MutationMapping
    fun likePost(postId: UUID): Post {}

    @MutationMapping
    fun unlikePost(postId: UUID): Post {}

    @MutationMapping
    fun updatePassword(password: String): User {}

    /*
    // @SchemaMapping
    @QueryMapping
    fun getUsers(@Argument userSearch: UserSearch): List<User> {
        if (!EnvironmentData.bucket.tryConsume(1))
            throw ThrottleRequestException("You are sending too many requests, please wait and try again.")

        val startTime = System.nanoTime()

        // Begin Neo4J Session
        val session = sessionFactory.openSession()

        // Generate Filters
        val usernameEqualsFilter = if (userSearch.usernameEquals != null && userSearch.usernameEquals != "")
            Filter("username", ComparisonOperator.EQUALS, userSearch.usernameEquals)
        else
            Filter("username", ComparisonOperator.EXISTS)

        val usernameContainsFilter = if (userSearch.usernameContains != null && userSearch.usernameContains != "")
            Filter("username", ComparisonOperator.CONTAINING, userSearch.usernameContains)
        else
            Filter("username", ComparisonOperator.EXISTS)

        val displayNameFilter = if (userSearch.displayName != null)
            Filter("displayName", ComparisonOperator.CONTAINING, userSearch.displayName)
        else
            Filter("displayName", ComparisonOperator.EXISTS)

        val bioFilter = if (userSearch.bio != null)
            Filter("bio", ComparisonOperator.CONTAINING, userSearch.bio)
        else
            Filter("bio", ComparisonOperator.EXISTS)

        val finalFilter = usernameEqualsFilter
            .and(usernameContainsFilter)
            .and(displayNameFilter)
            .and(bioFilter)

        // Query and filter results from the database
        val usersNodes: List<UserNode> = session.loadAll(UserNode::class.java, finalFilter, Pagination(0, 25)).toList()
        if (usersNodes.isEmpty())
            throw BirdException("No Users Found")

        // Convert Neo4j users to GraphQL users
        val users: List<User> = usersNodes.map { user -> user.toUser() }
        val endTime = System.nanoTime()
        println("GetUser T: ${(endTime - startTime) / 1_000_000.0}")
        println("FollowedBy: ${usersNodes[0].followedBy.size}")

        return users
    }

    fun createUserRealistic(
        users: MutableList<UserNode>,
        totalDegree: Int,
        username: String,
        displayName: String
    ): MutableList<UserNode> {

        val userNode = UserNode(
            UUID.randomUUID().toString(),
            "$username@example.com",
            username,
            displayName,
            BCrypt.hashpw("12345678", BCrypt.gensalt(12)),
        )

        // Print Current User
//        println("CreateUser: ${users.size + 1}")

        // Randomly add a follow based on popularity
        if (users.size > 0) {
            println("Add Following (Degree:$totalDegree)")
            var idx = 0
            var r: Int = nextInt(totalDegree)
//            println("Starting (r:$r)")

            while (idx < users.size - 1) {
                r -= users[idx].followedBy.size
//                println("(idx: $idx | r: $r)")
                if (r <= 0.0) break
                idx++
            }
//            println("Adding User (ID:$idx)")
            val otherNode: UserNode = users[idx]
            userNode.following.add(otherNode)
            otherNode.followedBy.add(userNode)
        }

        // Randomly Create "Original" Root Posts (1-9) inclusive
        for (j in 1 until nextInt(10)) {
            val post = PostNode(
                UUID.randomUUID().toString(),
                "${userNode.displayName}'s Chrip #$j",
                "The post body can be much longer, I love cheese"
            )

            userNode.authored.add(post)
            post.authoredBy.add(userNode)
        }

        // Randomly add a reChirp based on following popularity [BROKEN: STACKOVERFLOW]
//        if (users.size > 0) {
//            println("Add Following (Degree:$totalDegree)")
//            var idx = 0
//            var r: Int = nextInt(totalDegree)
//
//            while (idx < users.size - 1) {
//                r -= users[idx].followedBy.size
//                if (r <= 0.0) break
//                idx++
//            }
//            val otherUser: UserNode = users[idx]
//
//            // Get a random post by this user
//            val otherPost: PostNode = otherUser.authored[nextInt(otherUser.authored.size)]
//
//            val post: PostNode = PostNode(
//                UUID.randomUUID().toString(),
//                "${userNode.displayName}'s re Chirp",
//                "Wow, I literally hate you"
//            )
//
//            // First set this post as "Authored" by this user
//            userNode.authored.add(post)
//            // Then set this post's parent
//            post.parentPost = otherPost
//            // Then add this post as one of the parent's children
//            otherPost.childPosts.add(post)
//        }

        users.add(userNode)
        return users
    }

    @MutationMapping
    fun createUsers(@Argument num: Int): String {
        if (!EnvironmentData.bucket.tryConsume(2)) //200
            throw ThrottleRequestException("You are sending too many requests, please wait and try again.")

        val csvReader = CSVReader(FileReader("names.csv"))

        val names = mutableListOf<String>()

        // we are going to read data line by line
        var nextRecord: Array<String>? = csvReader.readNext()
        while (nextRecord != null) {
            names.add(nextRecord[0])
            nextRecord = csvReader.readNext()
        }

        var users = mutableListOf<UserNode>()
//        val users = Collections.synchronizedList(mutableListOf<UserNode>()) // Thread Safe

        var totalDegree: Int = 0

        for (i in 0 until num) {
            val name1 = names[nextInt(1080)]
            val name2 = names[nextInt(1080)]
            val username = "${name1.lowercase()}-${name2.lowercase()}"
            val displayName = "${name1.uppercase()} ${name2.uppercase()}"

            println("User : ${users.size + 1} : Time : ${measureTimeMillis{ users = createUserRealistic(users, totalDegree, username, displayName) }}")
            println("-------------------------------")
            totalDegree += 2
        }

        // Begin Neo4J Session
        val session = sessionFactory.openSession()

        session.save(users)

//        println("CreateUsers T: ${(endTime - startTime) / 1_000_000.0}")

        return "Success"
    }

    @MutationMapping
    fun createUser(@Argument userCreate: UserCreate): User {
        if (!EnvironmentData.bucket.tryConsume(200))
            throw ThrottleRequestException("You are sending too many requests, please wait and try again.")

        val userNode: UserNode

        val time = measureTimeMillis {
            // Begin Neo4J Session
            val session = sessionFactory.openSession()

            val emailFilter = Filter("email", ComparisonOperator.EQUALS, userCreate.username)
            val emailTaken = session.loadAll(UserNode::class.java, emailFilter, Pagination(0, 10)).isNotEmpty()
            val usernameFilter = Filter("username", ComparisonOperator.EQUALS, userCreate.username)
            val usernameTaken = session.loadAll(UserNode::class.java, usernameFilter, Pagination(0, 10)).isNotEmpty()

            if (emailTaken)
                throw BirdException("Email Already Used")
            if (usernameTaken)
                throw BirdException("Username Taken")

            userNode = UserNode(
                UUID.randomUUID().toString(),
                userCreate.email,
                userCreate.username,
                userCreate.displayName,
                BCrypt.hashpw(userCreate.password, BCrypt.gensalt(16)),
            )

            session.save(userNode)
        }
        println("CreateUser T: $time")

        return userNode.toUser()


//        val tx = session.beginTransaction()
//
//        try {
//            val userNode = UserNode(
//                UUID.randomUUID().toString(),
//                userCreate.email,
//                userCreate.username,
//                userCreate.displayName,
//                userCreate.password,
//                "",
//                "",
//                "",
//                false,
//                0,
//                0,
//                0
//            )
//
//            tx.commit()
//            session.save(userNode)
//
//            val endTime = System.nanoTime()
//            println("CreateUser T: ${(endTime - startTime) / 1_000_000.0}")
//
//            return userNode.toUser()
//        } catch (e: Exception) {
//            tx.rollback()
//            println("ROLLBACK: $e")
//        } finally {
//            tx.close()
//        }
//
//        throw BirdException("There was an error saving your request.")
    }
    */
}
