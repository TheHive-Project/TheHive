(function() {
  "use strict";
  angular
    .module("theHiveServices")
    .factory("AuthenticationSrv", function($http, $q, UtilsSrv, SecuritySrv) {
      var self = {
        currentUser: null,
        login: function(username, password) {
          return $http
            .post("./api/login", {
              user: username,
              password: password
            });
        },
        logout: function(success, failure) {
          $http
            .get("./api/logout")
            .then(function(data, status, headers, config) {
              self.currentUser = null;

              if (angular.isFunction(success)) {
                success(data, status, headers, config);
              }
            })
            .catch(function(data, status, headers, config) {
              if (angular.isFunction(failure)) {
                failure(data, status, headers, config);
              }
            });
        },
        current: function() {
          var result = {};
          return $http
            .get("./api/v1/user/current")
            .then(function(response) {
              self.currentUser = response.data;
              UtilsSrv.shallowClearAndCopy(response.data, result);

              return $q.resolve(result);
            })
            .catch(function(err) {
              self.currentUser = null;
              return $q.reject(err);
            });
        },
        ssoLogin: function(code) {
          var url = angular.isDefined(code) ? "./api/ssoLogin?code=" + code : "./api/ssoLogin";
          return $http.post(url, {});
        },
        // isAdmin: function(user) {
        //   var u = user;
        //   var re = /admin/i;
        //   return re.test(u.roles);
        // },
        isSuperAdmin: function() {
            var user = self.currentUser;

            return user && user.organisation === 'default';
        },
        hasPermission: function(permissions) {
            var user = self.currentUser;

            if (!user) {
                return false;
            }

            //return !_.isEmpty(_.intersection(user.permissions, permissions));

            return SecuritySrv.checkPermissions(user.permissions, permissions);
        }
      };

      return self;
    });
})();
