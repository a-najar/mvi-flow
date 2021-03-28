package com.geniusforapp.mvi

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ContentLoadingProgressBar
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.*
import com.geniusforapp.mvi.Actions.LoginRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<MainViewModel>()

    private val editEmail by lazy { findViewById<TextInputEditText>(R.id.editEmail) }
    private val editPassword by lazy { findViewById<TextInputEditText>(R.id.editPassword) }
    private val buttonLogin by lazy { findViewById<MaterialButton>(R.id.buttonLogin) }
    private val progressBar by lazy { findViewById<ContentLoadingProgressBar>(R.id.progressBar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        viewModel.state().observe(this@MainActivity, ::render)


        lifecycleScope.launch(Dispatchers.Main) {
            editEmail.textChanges()
                .combine(editPassword.textChanges(), ::LoginRequest)
                .flatMapConcat { pair -> buttonLogin.clicks().map { pair } }
                .onEach { viewModel.dispatch(it) }
                .collect { }
        }

    }

    private fun render(state: State) {
        progressBar.let {
            if (state.isLoading) {
                it.show()
            } else {
                it.hide()
            }
        }
        editEmail.isEnabled = !state.isLoading
        editPassword.isEnabled = !state.isLoading


        state.isLogged.takeIf { it }?.let {
            Toast.makeText(this, "User Is Logged", Toast.LENGTH_SHORT).show()
        }
    }


}

private fun EditText.textChanges(): Flow<String> {
    return callbackFlow {
        addTextChangedListener { offer(it.toString()) }
        awaitClose { removeTextChangedListener(null) }
    }
}

private fun Button.clicks(): Flow<Unit> {
    return callbackFlow {
        setOnClickListener { offer(Unit) }
        awaitClose { setOnClickListener(null) }
    }
}


interface AuthRepository {
    @Throws(IllegalArgumentException::class)
    suspend fun login(email: String, password: String): Flow<Boolean>

    @Throws(IllegalArgumentException::class)
    suspend fun register(email: String, password: String, displayName: String): Flow<Boolean>
}


class AuthRepositoryImplementation : AuthRepository {
    override suspend fun login(email: String, password: String): Flow<Boolean> {
        return flow {
            delay(2000)
            emit(true)
        }
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Flow<Boolean> {
        return flow {
            delay(3000)
            emit(true)
        }
    }

}

class MainViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImplementation(),
    private val main: CoroutineDispatcher = Dispatchers.Main,
    io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val initState: State = State()
    private val actions = MutableStateFlow<Actions>(Actions.Init)

    private val reducer: suspend (State, Change) -> State = { state, change ->
        when (change) {
            is Change.LoadingChange -> state.copy(isIdle = false, isLoading = true)
            is Change.ErrorChange -> state.copy(
                throwable = change.throwable,
                isLoading = false
            )
            is Change.LoggedChange -> state.copy(
                isLoading = false,
                isLogged = change.isLogged
            )

            is Change.RegisteredChange -> state.copy(
                isLoading = false,
                isRegistered = change.isRegistered
            )
        }
    }


    private val loginChanges = actions
        .filterIsInstance<LoginRequest>()
        .transform {
            emit(Change.LoadingChange)
            emitAll(authRepository.login(it.email, it.password)
                .map { result -> Change.LoggedChange(result) })
        }
        .catch { emit(Change.ErrorChange(it)) }
        .flowOn(io)

    private val registerChanges = actions
        .filterIsInstance<Actions.RegisterRequest>()
        .transform {
            emit(Change.LoadingChange)
            emitAll(authRepository.register(
                it.email,
                it.password,
                it.displayName
            ).map { result -> Change.RegisteredChange(result) })
        }
        .catch { emit(Change.ErrorChange(it)) }
        .flowOn(io)


    fun state(): LiveData<State> = merge(loginChanges, registerChanges)
        .scan(initState, reducer)
        .asLiveData(main)

    fun dispatch(action: Actions) {
        viewModelScope.launch { actions.emit(action) }
    }

}

sealed class Change {
    object LoadingChange : Change()
    data class LoggedChange(val isLogged: Boolean) : Change()
    data class RegisteredChange(val isRegistered: Boolean) : Change()
    data class ErrorChange(val throwable: Throwable?) : Change()
}

data class State(
    val isIdle: Boolean = true,
    val isLoading: Boolean = false,
    val isLogged: Boolean = false,
    val isRegistered: Boolean = false,
    val throwable: Throwable? = null
)

sealed class Actions {
    data class LoginRequest(val email: String, val password: String) : Actions()
    data class RegisterRequest(val email: String, val password: String, val displayName: String) :
        Actions()

    object Init : Actions()
}