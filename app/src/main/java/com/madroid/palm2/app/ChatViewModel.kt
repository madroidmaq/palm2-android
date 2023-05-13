package com.madroid.palm2.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.generativelanguage.v1beta2.DiscussServiceClient
import com.google.ai.generativelanguage.v1beta2.DiscussServiceSettings
import com.google.ai.generativelanguage.v1beta2.Example
import com.google.ai.generativelanguage.v1beta2.GenerateMessageRequest
import com.google.ai.generativelanguage.v1beta2.Message
import com.google.ai.generativelanguage.v1beta2.MessagePrompt
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider
import com.google.api.gax.rpc.FixedHeaderProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/**
 * @author madroid(maanqing@zepp.com)
 */
class ChatViewModel: ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(value = listOf())
    val messages: StateFlow<List<Message>>
        get() = _messages

    private var client: DiscussServiceClient

    init {
        // Initialize the Discuss Service Client
        client = initializeDiscussServiceClient(
            apiKey = "<insert your api key here>"
        )

        // Create the message prompt
        val prompt = createPrompt("How tall is the Eiffel Tower?")

        // Send the first request to kickstart the conversation
        val request = createMessageRequest(prompt)
        generateMessage(request)
    }
    private fun generateMessage(
        request: GenerateMessageRequest
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = client.generateMessage(request)

                val returnedMessage = response.candidatesList.last()
                // display the returned message in the UI
                _messages.update {
                    // Add the response to the list
                    it.toMutableList().apply {
                        add(returnedMessage)
                    }
                }
            } catch (e: Exception) {
                // There was an error, let's add a new message with the details
                _messages.update { messages ->
                    val mutableList = messages.toMutableList()
                    mutableList.apply {
                        add(
                            Message.newBuilder()
                                .setAuthor("API Error")
                                .setContent(e.message)
                                .build()
                        )
                    }
                }
            }
        }
    }

    private fun initializeDiscussServiceClient(
        apiKey: String
    ): DiscussServiceClient {
        // (This is a workaround because GAPIC java libraries don't yet support API key auth)
        val transportChannelProvider = InstantiatingGrpcChannelProvider.newBuilder()
            .setHeaderProvider(FixedHeaderProvider.create(hashMapOf("x-goog-api-key" to apiKey)))
            .build()

        // Create DiscussServiceSettings
        val settings = DiscussServiceSettings.newBuilder()
            .setTransportChannelProvider(transportChannelProvider)
            .setCredentialsProvider(FixedCredentialsProvider.create(null))
            .build()

        // Initialize a DiscussServiceClient
        val discussServiceClient = DiscussServiceClient.create(settings)

        return discussServiceClient
    }
    private fun createCaliforniaExample(): Example {
        val input = Message.newBuilder()
            .setContent("What is the capital of California?")
            .build()

        val response = Message.newBuilder()
            .setContent("If the capital of California is what you seek, Sacramento is where you ought to peek.")
            .build()

        val example = Example.newBuilder()
            .setInput(input)
            .setOutput(response)
            .build()

        return example
    }

    private fun createPrompt(
        messageContent: String
    ): MessagePrompt {
        val palmMessage = Message.newBuilder()
            .setAuthor("0")
            .setContent(messageContent)
            .build()

        val messagePrompt = MessagePrompt.newBuilder()
            .addMessages(palmMessage) // required
            .setContext("Respond to all questions with a rhyming poem.") // optional
            .addExamples(createCaliforniaExample()) // use addAllExamples() to add a list of examples
            .build()

            // Add this line to update the UI
            _messages.update {
                it.toMutableList().apply {
                    add(palmMessage)
                }
            }

            return messagePrompt
    }


    private fun createMessageRequest(prompt: MessagePrompt): GenerateMessageRequest {
        return GenerateMessageRequest.newBuilder()
            .setModel("models/chat-bison-001") // Required, which model to use to generate the result
            .setPrompt(prompt) // Required
            .setTemperature(0.5f) // Optional, controls the randomness of the output
            .setCandidateCount(1) // Optional, the number of generated messages to return
            .build()
    }


    fun sendMessage(userInput: String) {
        val prompt = createPrompt(userInput)

        val request = createMessageRequest(prompt)
        generateMessage(request)
    }


}