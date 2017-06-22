(function() {
    'use strict';
    angular.module('theHiveServices').factory('ListSrv', function($resource) {
        return $resource('./api/list/:listId', {}, {
            query: {
                method: 'GET',
                isArray: false
            },
            add: {
                method: 'PUT'
            },
            update: {
                url: './api/list/:itemId',
                method: 'PATCH',
            }
        });
    });
})();
