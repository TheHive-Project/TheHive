(function () {
    'use strict';
    angular.module('theHiveServices')
        .factory('CustomFieldsSrv', function ($http) {

            var factory = {
                removeField: function (field) {
                    return $http.delete('./api/list/' + field.id);
                },
                usage: function(field) {
                    return $http.get('./api/customFields/' + field.reference);
                }
            };

            return factory;
        });
})();
