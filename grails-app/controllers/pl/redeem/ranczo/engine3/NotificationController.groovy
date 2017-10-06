package pl.redeem.ranczo.engine3

import com.github.sheigutn.pushbullet.Pushbullet
import com.github.sheigutn.pushbullet.items.device.Device
import com.github.sheigutn.pushbullet.items.file.UploadFile
import com.github.sheigutn.pushbullet.items.push.sendable.defaults.SendableFilePush
import com.github.sheigutn.pushbullet.items.push.sendable.defaults.SendableNotePush
import com.github.sheigutn.pushbullet.items.push.sent.Push
import grails.converters.JSON
import pl.redeem.ranczo.pushbullet.PushbulletProxy

class NotificationController {

    def index() {

        def apiToken = "o.uk2IG2fATXXS3gFSylil8UFnuqVx9Ctq"
        Pushbullet pushbullet = new PushbulletProxy(apiToken);

        /*SendableNotePush note = new SendableNotePush()
        note.title = "TEST from JAVA-CLI - title"
        note.body = "TEST BODY"*/

        File f = new File("d:\\_mk\\_prv\\eurokam\\wizualizacja.JPG");
        UploadFile file = pushbullet.uploadFile(f);

        SendableFilePush note = new SendableFilePush("Ala ma Kota", file)

        pushbullet.pushToAllDevices(note);

        def ret = [
                status: "success",
                //device: device
        ]

        render ret as JSON

    }

    def create() {
        def apiToken = "o.uk2IG2fATXXS3gFSylil8UFnuqVx9Ctq"
        Pushbullet pushbullet = new PushbulletProxy(apiToken);

        Device newDev = pushbullet.createDevice("RanczoEngine", "stream");

        def ret = [
                status: "success",
                device: newDev
        ]

        render ret as JSON

    }

    def list() {
        def apiToken = "o.uk2IG2fATXXS3gFSylil8UFnuqVx9Ctq"
        Pushbullet pushbullet = new PushbulletProxy(apiToken);

        Device ranczo = pushbullet.getDevice("RanczoEngine");

        List<Push> notifList = ranczo.getReceivedPushes();

        notifList.forEach{ it->
            System.out.println("push ${it}")
        }

        def ret = [
                status: "success",
//                pushes: notifList
        ]

        render ret as JSON
    }
}
