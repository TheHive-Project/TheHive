(function () {
    'use strict';
    angular.module('theHiveServices')
        .factory('CustomFieldsSrv', function ($http) {

            var convert = function(field) {
                return {
                    reference: field.name,
                    name: field.displayName,
                    description: field.description,
                    options: field.options,
                    type: field.type
                    //TODO add Mandaroty
                };
            };

            var factory = {

                removeField: function (field) {
                    return $http.delete('./api/list/' + field.id);
                },
                usage: function(field) {
                    return $http.get('./api/customFields/' + field.reference);
                },
                list: function () {
                    return $http.get('./api/customField');
                },
                create: function (field) {
                    return $http.post('./api/customField', convert(field));
                },
                update: function (id, field) {
                    return $http.patch('./api/customField', convert(field));
                }

            };

            return factory;
        });
})();
