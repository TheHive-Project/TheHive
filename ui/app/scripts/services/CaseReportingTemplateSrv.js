(function() {
    'use strict';
    angular.module('theHiveServices').service('CaseReportingTemplateSrv', function($q, $http) {
		this.baseUrl = './api/case/reporting/template'
		
        this.list = function() {
            var defer = $q.defer();
            $http.post(this.baseUrl + '/_search', {}, {
                params: {
                    range: 'all'
                }
            }).then(function(response) {
                defer.resolve(response.data);
            }, function(err) {
                defer.reject(err);
            });
            return defer.promise;
        };

        this.get = function(id) {
            var defer = $q.defer();
            $http.get(this.baseUrl + '/' + id).then(function(response) {
                defer.resolve(response.data);
            }, function(err) {
                defer.reject(err);
            });
            return defer.promise;
        };

        this.delete = function(id) {
            return $http.delete(this.baseUrl + '/' + id);
        };

        this.create = function(template) {
            return $http.post(this.baseUrl, template);
        };

        this.update = function(id, template) {
            return $http.patch(this.baseUrl + '/' + id, template);
        };
        
		this.import = function(post) {
			var postData = {
				template: post.attachment
			};
			
			return $http({
				method: 'POST',
				url: this.baseUrl + '/_import',
				headers: {
					'Content-Type': undefined
				},                        
				transformRequest: function (data) {
					var formData = new FormData(),
						copy = angular.copy(data, {}),
						_json = {};

					angular.forEach(data, function (value, key) {
						if (Object.getPrototypeOf(value) instanceof Blob || Object.getPrototypeOf(value) instanceof File) {
							formData.append(key, value);
							delete copy[key];
						} 
					});

					return formData;
				},  
				data: postData
			});
		};
    });
})();
