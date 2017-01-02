(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('UserInfoSrv', function(UserSrv) {

            this.userCache = {};

            this.get = function(userId) {
                if (angular.isDefined(this.userCache[userId])) {
                    return this.userCache[userId];
                } else {
                    this.userCache[userId] = UserSrv.getInfo(userId);
                    return this.userCache[userId];
                }
            };

            this.clear = function() {
                this.userCache = {};
            };

            this.remove = function(userId) {
                delete this.userCache[userId];
            };

            this.update = function(userId, userData) {
                this.userCache[userId] = userData;
            };

        });
})();
