from frontend import frontend
from user import user
from admin import admin
from tournament import tournament

from flask import Flask, render_template, request, redirect, url_for
from flask_wtf import FlaskForm 
from wtforms import StringField, PasswordField ,SubmitField 
from wtforms.validators import InputRequired
from werkzeug.security import generate_password_hash 
import requests
import json

# instance of flask application
application = Flask(__name__)
application.register_blueprint(frontend) 
application.register_blueprint(user, url_prefix='/user') 
application.register_blueprint(admin, url_prefix='/admin') 
application.register_blueprint(tournament, url_prefix='/tournament') 
application.config['SECRET_KEY'] = 'secretkey'


@application.route("/fetch_api")
def api_fetch_example():
    url = "https://api.openweathermap.org/data/2.5/weather?lat=1.295895&lon=103.8474269&appid=0e0816d27fe6cc9cba94aa06cf871e94"
    res = requests.get(url)
    print(res.json())
    return render_template('fetch_api.html', data=str(res.json()))

class MyForm(FlaskForm): 
    user_input = StringField('Name', validators=[InputRequired()]) 

@application.route("/post_api", methods=['GET', 'POST'])
def api_post_example():
    form = MyForm()
    data = "Type something first and submit."
    if request.method == 'POST' and form.validate_on_submit():
        print(form.user_input.data)
        response = requests.post("https://httpbin.org/post", 
            data={"key": form.user_input.data},
            headers={"Content-Type": "application/json"},
        )
        # response_dict = json.loads(response.json())
        print(response_dict)
        data = str(response.json())
        # return redirect(url_for('post_output'))
    return render_template('post_api.html', form=form, data=data)

@application.errorhandler(400)
def bad_request(e):
    return render_template('errors/400.html'), 400

@application.errorhandler(401)
def not_authenticated(e):
    return render_template('errors/401.html'), 401

@application.errorhandler(403)
def forbidden(e):
    return render_template('errors/403.html'), 403

@application.errorhandler(404)
def page_not_found(e):
    return render_template('errors/404.html'), 404

if __name__ == '__main__':  
   application.run(debug=True) # remove debug=True for production