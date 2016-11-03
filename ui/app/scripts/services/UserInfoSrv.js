(function() {
    'use strict';
    angular.module('theHiveServices').factory('UserInfoSrv', function(UserSrv) {
        var userCache = {};
        return function(login) {
            if (angular.isDefined(userCache[login])) {
                return userCache[login];
            } else {
                userCache[login] = UserSrv.getInfo(login);                
                return userCache[login];
            }
        };
    });
})();
