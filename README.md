# Intern_Gemini
This is a basic Chat Application for customer support using Twilio's Programmable chat.

To get started

- You'll need to deploy the backend using : https://github.com/TwilioDevEd/twiliochat-node \
Clone the above repository and follow the instructions in their **README**.

- After you have exposed your localhost to the internet via ngrok, you will recieve a url hosting your localhost as **(http://ec059c3e10ee.ngrok.io)**, you will need to update the **token url** in **Project -> res -> values -> keys.xml** as _http://example.ngrok.io/token_

- Now, you can run the application on real devices.

- You will need to log in as **agent1** in one of the devices as the default agent is set as **agent1** which you can replace in the _setChannel_ function in MainChatActivity.java.


**NOTES**

- You can switch between private and public channels using the USER/ALL tab in the navigation bar.
- Abruptly destroying the program may sometime require you to log out the user from backend. It was fixed but still remains to be tested for all scenarios.
- On requesting support, a private channel is created for the _user_, and when the _user_ joins the support channel, _agent_ is sent an invitation to join the channel.
- If the user who owns the private channel leaves the channel, the channel gets deleted automatically.
